package io.youi.client

import io.youi.ajax.{AjaxAction, AjaxRequest}
import io.youi.http.content._
import io.youi.http.{Headers, HttpRequest, HttpResponse, HttpStatus}
import io.youi.net.ContentType

import scala.concurrent.Future
import scribe.Execution.global

case class JSHttpClient(config: HttpClientConfig = HttpClient.config) extends HttpClient {
  private val HeaderRegex = """(.+)[=](.+)""".r

  override protected def implementation(request: HttpRequest): Future[HttpResponse] = {
    val manager = config.connectionPool.asInstanceOf[JSConnectionPool].manager
    val ajaxRequest = new AjaxRequest(
      url = request.url,
      data = request.content.map(content2String),
      timeout = 0,
      headers = request.headers.map.flatMap(t => t._2.map(value => t._1 -> value)),
      withCredentials = true,
      responseType = ""
    )
    val action = new AjaxAction(ajaxRequest)
    manager.enqueue(action).map { xmlHttpRequest =>
      val headers: Map[String, List[String]] = xmlHttpRequest.getAllResponseHeaders().split('&').map {
        case HeaderRegex(key, value) => key -> value
      }.groupBy(_._1).map {
        case (key, array) => key -> array.toList.map(_._2)
      }
      val content = xmlHttpRequest.responseType match {
        case null | "" => None
        case _ => {
          val `type` = ContentType.parse(xmlHttpRequest.responseType)
          Some(Content.string(xmlHttpRequest.responseText, `type`))
        }
      }
      HttpResponse(
        status = HttpStatus(xmlHttpRequest.status, xmlHttpRequest.statusText),
        headers = Headers(headers),
        content = content
      )
    }
  }

  override protected def content2String(content: Content): String = content match {
    case c: StringContent => c.value
    case _ => throw new RuntimeException(s"$content not supported")
  }
}