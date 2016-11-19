package io.youi.http

import java.nio.ByteBuffer

import com.outr.props.{Channel, State, Var}

import scala.collection.mutable.ListBuffer

trait WebSocketListener {
  private[http] val _connected = Var[Boolean](false)
  val connected: State[Boolean] = _connected.asState

  val send = new WebSocketChannels
  val receive = new WebSocketChannels
  val error: Channel[Throwable] = Channel[Throwable]

  init()

  // Set up backlog for sending until connection has been established
  private def init(): Unit = {
    val backlog = ListBuffer.empty[Any]

    val textListener: String => Unit = (t: String) => {
      backlog += t
    }
    val binaryListener: Array[ByteBuffer] => Unit = (b: Array[ByteBuffer]) => {
      backlog += b
    }
    send.text.attach(textListener)
    send.binary.attach(binaryListener)

    connected.attach { b =>
      if (b) {
        send.text.detach(textListener)
        send.binary.detach(binaryListener)
        backlog.foreach {
          case s: String => send.text := s
          case b: Array[ByteBuffer] => send.binary := b
        }
        backlog.clear()
      }
    }
  }

  def close(): Unit = send.close := Unit
}

class WebSocketChannels {
  val text: Channel[String] = Channel[String]
  val binary: Channel[Array[ByteBuffer]] = Channel[Array[ByteBuffer]]
  val close: Channel[Unit] = Channel[Unit]
}

object WebSocketListener {
  val key = "webSocketListener"
}
