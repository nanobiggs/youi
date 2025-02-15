package io.youi.server

import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer

import io.undertow.io.{IoCallback, Sender}
import io.undertow.predicate.Predicates
import io.undertow.protocols.ssl.UndertowXnioSsl
import io.undertow.server.handlers.encoding.{ContentEncodingRepository, DeflateEncodingProvider, EncodingHandler, GzipEncodingProvider}
import io.undertow.server.handlers.form.{FormDataParser, FormParserFactory}
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient
import io.undertow.server.handlers.resource.URLResource
import io.undertow.server.{HttpServerExchange, HttpHandler => UndertowHttpHandler}
import io.undertow.util.{HeaderMap, HttpString, SameThreadExecutor}
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core._
import io.undertow.websockets.extensions.PerMessageDeflateHandshake
import io.undertow.websockets.spi.WebSocketHttpExchange
import io.undertow.{Handlers, Undertow, UndertowOptions}
import io.youi.http._
import io.youi.http.content._
import io.youi.net.{ContentType, IP, Parameters, Path, URL}
import io.youi.server.util.SSLUtil
import org.powerscala.io._
import org.xnio.{OptionMap, Xnio}

import scala.collection.JavaConverters._
import scribe.Execution.global

class UndertowServerImplementation(val server: Server) extends ServerImplementation with UndertowHttpHandler {
  val enableHTTP2: Boolean = Server.config("enableHTTP2").opt[Boolean].getOrElse(true)
  val webSocketCompression: Boolean = Server.config("webSocketCompression").opt[Boolean].getOrElse(true)

  private var instance: Option[Undertow] = None

  override def start(): Unit = synchronized {
    val contentEncodingRepository = new ContentEncodingRepository()
      .addEncodingHandler("gzip", new GzipEncodingProvider, 100, Predicates.maxContentSize(5L))
      .addEncodingHandler("deflate", new DeflateEncodingProvider, 50, Predicates.maxContentSize(5L))
    val encodingHandler = new EncodingHandler(contentEncodingRepository).setNext(this)

    val builder = Undertow.builder().setHandler(encodingHandler)
    if (enableHTTP2) {
      builder.setServerOption(UndertowOptions.ENABLE_HTTP2, java.lang.Boolean.TRUE)
    }
    server.config.listeners.foreach {
      case HttpServerListener(host, port, enabled) => if (enabled) {
        builder.addHttpListener(port, host)
      }
      case HttpsServerListener(host, port, keyStore, enabled) => if (enabled) {
        try {
          val sslContext = SSLUtil.createSSLContext(keyStore.location, keyStore.password)
          builder.addHttpsListener(port, host, sslContext)
        } catch {
          case t: Throwable => {
            throw new RuntimeException(s"Error loading HTTPS, host: $host, port: $port, keyStore: ${keyStore.path}", t)
          }
        }
      }
    }
    val u = builder.build()
    u.start()
    instance = Some(u)
  }

  override def stop(): Unit = synchronized {
    instance match {
      case Some(u) => {
        u.stop()
        instance = None
      }
      case None => // Not running
    }
  }

  override def isRunning: Boolean = instance.nonEmpty

  private val formParserBuilder = FormParserFactory.builder()

  override def handleRequest(exchange: HttpServerExchange): Unit = server.errorSupport {
    val url = URL(s"${exchange.getRequestURL}?${exchange.getQueryString}")

    exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable {
      override def run(): Unit = {
        try {
          server.proxies.find(_.matches(url)) match {
            case Some(proxy) => UndertowServerImplementation.handleProxy(UndertowServerImplementation.this, url, exchange, proxy)
            case None => {
              if (exchange.getRequestContentLength > 0L && exchange.getRequestHeaders.getFirst("Content-Type").startsWith("multipart/form-data")) {
                if (exchange.isInIoThread) {
                  exchange.dispatch(this)
                } else {
                  exchange.startBlocking()
                  val formDataParser = formParserBuilder.build().createParser(exchange)
                  formDataParser.parseBlocking()
                  requestHandler(exchange, url)
                }
              } else {
                requestHandler(exchange, url)
              }
            }
          }
        } catch {
          case exc: ServerException => throw exc
          case t: Throwable => new ServerException("Error Handling Request", t, url)
        }
      }
    })
  }

  private def requestHandler(exchange: HttpServerExchange, url: URL): Unit = {
    UndertowServerImplementation.processRequest(exchange, url) { request =>
      try {
        val connection: HttpConnection = HttpConnection(server, request)
        server.handle(connection).foreach { c =>
          UndertowServerImplementation.response(this, c, exchange)
        }
      } catch {
        case t: Throwable => scribe.error(t)
      }
    }
  }
}

object UndertowServerImplementation extends ServerImplementationCreator {
  override def create(server: Server): ServerImplementation = {
    new UndertowServerImplementation(server)
  }

  def parseHeaders(headerMap: HeaderMap): Headers = Headers(headerMap.asScala.map { hv =>
    hv.getHeaderName.toString -> hv.asScala.toList
  }.toMap)

  def processRequest(exchange: HttpServerExchange, url: URL)(handler: HttpRequest => Unit): Unit = {
    val source = IP(exchange.getSourceAddress.getAddress.getHostAddress)
    val headers = parseHeaders(exchange.getRequestHeaders)

    def handle(content: Option[Content]): Unit = {
      val request = HttpRequest(
        method = Method(exchange.getRequestMethod.toString),
        source = source,
        url = url,
        headers = headers,
        content = content
      )
      handler(request)
    }

    if (exchange.getRequestContentLength > 0L) {
      Headers.`Content-Type`.value(headers).getOrElse(ContentType.`text/plain`) match {
        case ContentType.`multipart/form-data` => {
          val formData = exchange.getAttachment(FormDataParser.FORM_DATA)
          val data = formData.asScala.toList.map { key =>
            val entries: List[FormDataEntry] = formData.get(key).asScala.map { entry =>
              val headers = parseHeaders(entry.getHeaders)
              if (entry.isFileItem) {
                val path = entry.getFileItem.getFile
                FileEntry(entry.getFileName, path.toFile, headers)
              } else {
                StringEntry(entry.getValue, headers)
              }
            }.toList
            FormData(key, entries)
          }
          handle(Some(FormDataContent(data)))
        }
        case ct => {
          def finish(exchange: HttpServerExchange): Unit = {
            exchange.startBlocking()
            val data = IO.stream(exchange.getInputStream, new StringBuilder).toString
            handle(Some(StringContent(data, ct)))
          }
          if (exchange.isInIoThread) {
            exchange.dispatch(new UndertowHttpHandler {
              override def handleRequest(exchange: HttpServerExchange): Unit = finish(exchange)
            })
          } else {
            finish(exchange)
          }
        }
      }
    } else {
      handle(None)
    }
  }

  def response(impl: UndertowServerImplementation, connection: HttpConnection, exchange: HttpServerExchange): Unit = {
    connection.webSocketSupport match {
      case Some(webSocketListener) => handleWebSocket(impl, connection, webSocketListener, exchange)
      case None => handleStandard(impl, connection, exchange)
    }
  }

  private def handleWebSocket(impl: UndertowServerImplementation,
                              httpConnection: HttpConnection,
                              connection: Connection,
                              exchange: HttpServerExchange): Unit = {
    val handler = Handlers.websocket(new WebSocketConnectionCallback {
      override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
        // Handle sending messages
        connection.send.text.attach { message =>
          WebSockets.sendText(message, channel, null)
        }
        connection.send.binary.attach { message =>
          WebSockets.sendBinary(message, channel, null)
        }
        connection.send.close.attach { _ =>
          channel.sendClose()
        }

        // Handle receiving messages
        channel.getReceiveSetter.set(new AbstractReceiveListener {
          override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit = {
            val data = message.getData
            connection.receive.text := data
            super.onFullTextMessage(channel, message)
          }

          override def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
            message.getData.getResource.foreach(connection.receive.binary := _)
            super.onFullBinaryMessage(channel, message)
          }

          override def onError(channel: WebSocketChannel, error: Throwable): Unit = {
            connection.error := error
            super.onError(channel, error)
          }

          override def onFullCloseMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
            connection.receive.close := Unit
            super.onFullCloseMessage(channel, message)
          }
        })
        channel.resumeReceives()
        connection._connected := true
      }
    })
    if (impl.webSocketCompression) {
      handler.addExtension(new PerMessageDeflateHandshake)
    }
    handler.handleRequest(exchange)
  }

  def handleProxy(implementation: UndertowServerImplementation, url: URL, exchange: HttpServerExchange, proxy: ProxyHandler): Unit = {
    val keyStore = proxy.keyStore
    val destination = proxy.proxy(url)
    val proxyClient = new LoadBalancingProxyClient
    val ssl = keyStore.map { ks =>
      val sslContext = SSLUtil.createSSLContext(ks.location, ks.password)
      new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, sslContext)
    }
    val uri = new URI(destination.copy(path = Path.empty, parameters = Parameters.empty, fragment = None).toString)
    proxyClient.addHost(uri, ssl.orNull)
    val proxyHandler = Handlers.proxyHandler(proxyClient)
    exchange.setRequestPath(destination.path.encoded)
    exchange.setRequestURI(destination.path.encoded)
    proxyHandler.handleRequest(exchange)
  }

  private def handleStandard(impl: UndertowServerImplementation, connection: HttpConnection, exchange: HttpServerExchange): Unit = {
    val response = {
      var response = connection.response

      // Add the Server header if not already set
      if (Headers.Response.`Server`.value(response.headers).isEmpty) {
        response = response.withHeader(Headers.Response.`Server`(impl.server.config.name()))
      }

      connection.response.content.map { content =>
        // Add Content-Type from Content if not already set on the response
        if (Headers.`Content-Type`.value(response.headers).isEmpty) {
          response = response.withHeader(Headers.`Content-Type`(content.contentType))
        }

        // Set the Content-Length from Content if not already set on the response
        if (Headers.`Content-Length`.value(response.headers).isEmpty && content.length != -1L) {
          response = response.withHeader(Headers.`Content-Length`(content.length))
        }

        response
      }.getOrElse(connection.response)
    }

    exchange.setStatusCode(response.status.code)
    response.headers.map.foreach {
      case (key, values) => exchange.getResponseHeaders.putAll(new HttpString(key), values.asJava)
    }
    if (exchange.getRequestMethod.toString != "HEAD") {
      response.content match {
        case Some(content) => content match {
          case StringContent(s, _, _) => {
            exchange.getResponseSender.send(s, new IoCallback {
              override def onComplete(exchange: HttpServerExchange, sender: Sender): Unit = {
                sender.close()
              }

              override def onException(exchange: HttpServerExchange, sender: Sender, exception: IOException): Unit = {
                sender.close()
                if (exception.getMessage == "Stream closed") {
                  scribe.warn(s"Stream closed for $s")
                } else {
                  impl.server.error(exception)
                }
              }
            })
          }
          case fc: FileContent => ResourceServer.serve(exchange, fc)
          case URLContent(url, _, _) => {
            val resource = new URLResource(url, "")
            resource.serve(exchange.getResponseSender, exchange, new IoCallback {
              override def onComplete(exchange: HttpServerExchange, sender: Sender): Unit = {
                sender.close()
              }

              override def onException(exchange: HttpServerExchange, sender: Sender, exception: IOException): Unit = {
                sender.close()
                if (exception.getMessage == "Stream closed") {
                  scribe.warn(s"Stream closed for $url")
                } else {
                  impl.server.error(exception)
                }
              }
            })
          }
          case c: BytesContent => {
            val buffer = ByteBuffer.wrap(c.value)
            exchange.getResponseSender.send(buffer, new IoCallback {
              override def onComplete(exchange: HttpServerExchange, sender: Sender): Unit = {
                sender.close()
              }

              override def onException(exchange: HttpServerExchange, sender: Sender, exception: IOException): Unit = {
                sender.close()
                if (exception.getMessage == "Stream closed") {
                  scribe.warn("Stream closed for BytesContent")
                } else {
                  impl.server.error(exception)
                }
              }
            })
          }
          case c: StreamContent => {
            val runnable = new Runnable {
              override def run(): Unit = try {
                exchange.startBlocking()
                val out = exchange.getOutputStream
                c.stream(out)
              } catch {
                case exc: IOException if exc.getMessage == "Stream closed" => scribe.warn("Stream closed for StreamContent")
                case t: Throwable => throw t
              }
            }
            if (exchange.isInIoThread) {    // Must move to async thread before blocking
              exchange.dispatch(runnable)
            } else {
              runnable.run()
            }
          }
        }
        case None => exchange.getResponseSender.send("", new IoCallback {
          override def onComplete(exchange: HttpServerExchange, sender: Sender): Unit = {
            sender.close()
          }

          override def onException(exchange: HttpServerExchange, sender: Sender, exception: IOException): Unit = {
            sender.close()
            impl.server.error(exception)
          }
        })
      }
    }
  }
}