package io.youi.example.ui

import io.youi._
import io.youi.component.{HTMLTextView, HTMLTextArea, ImageView}
import io.youi.example.screen.UIExampleScreen
import io.youi.font.GoogleFont
import io.youi.image.Image
import io.youi.net._
import io.youi.virtual.VirtualSizeSupport
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.youi.component.Container

class VirtualSizeExample extends UIExampleScreen with VirtualSizeSupport {
  override def title: String = "Virtual Size Example"
  override def path: Path = path"/examples/virtual.html"

  override def createUI(): Future[Unit] = for {
    img <- Image("/images/1024.jpg")
    fnt <- GoogleFont.`Open Sans`.`regular`.load()
    logo <- Image("/images/logo.svg")
  } yield {
    actual.width := container.size.width
    actual.height := container.size.height
    virtualWidth := 1500.0
    virtualHeight := 768.0

    container.children += new Container {
      size.width := 1500.0.vw
      size.height := 768.0.vh
    }

    val sideBar = new Container {
      size.width := 240.0.vw
      background := Color.Gray
      size.height := container.size.height
      position.top := 0.vy
      position.left := 0.vx
    }
    container.children += sideBar

    val navBar = new Container {
      size.width := container.size.width - sideBar.size.width
      size.height := 30.vh
      position.top := 0.vy
      position.left := sideBar.position.right
      background := Color.LightGray
    }
    container.children += navBar

    navBar.children += new ImageView {
      image := logo
      size.width := 80.vw
      size.height := size.width.get.vh / 4.vh
      position.top := (navBar.size.height - size.height) / 2
      position.left := 15
    }

    val messageInput = new HTMLTextArea() {
      size.height := navBar.size.height
      position.left := sideBar.position.right.get
      position.bottom := container.size.height
      size.width := container.size.width - sideBar.size.width - 30
    }
    container.children += messageInput

    val messageList = new Container {
      position.top := navBar.position.bottom
      position.left := sideBar.position.right
      background := Color.LightGreen
      size.height := container.size.height - navBar.size.height - messageInput.size.height
      size.width := container.size.width - sideBar.size.width
    }
    container.children += messageList

  }
}