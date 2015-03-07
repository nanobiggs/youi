package org.hyperscala.examples.fabricjs

import org.hyperscala.examples.Example
import org.hyperscala.fabricjs._
import org.hyperscala.html._
import org.powerscala.Color
import org.hyperscala.realtime._

/**
 * @author Matt Hicks <matt@outr.com>
 */
class FabricJSExample extends Example {
  require(FabricJS)

  val t = new tag.Canvas(id = "myCanvas", width = 400, height = 200)
  t.style.border := "1px solid black"
  contents += t

  val canvas = new StaticCanvas(t)
  canvas.backgroundColor := Color.immutable(100, 100, 200, 255)   // TODO: make Alpha Double
  val rect = new Rect {
    left := 100.0
    top := 100.0
    fill := Color.Red
    width := 20.0
    height := 20.0
    originX := "center"
    originY := "center"
  }
  canvas.contents += rect

  val image = new Image("/images/hyperscala.png") {
    left := 10.0
    top := 100.0
    width := 124.0
    height := 45.0
    angle := -45.0
  }
  canvas.contents += image

  val path = new Path("M 0 0 L 200 100 L 170 200 z") {
    left := 100.0
    originX := "center"
    originY := "center"
    fill := Color.Red
    strokeWidth := 5.0
    stroke := Color.Green
    opacity := 0.5
  }
  canvas.contents += path

  contents += new tag.Button(content = "Rotate") {
    clickEvent.onRealtime {
      case evt => rect.angle := rect.angle() + 22.5
    }
  }
  contents += new tag.Button(content = "Remove") {
    clickEvent.onRealtime {
      case evt => canvas.contents -= rect
    }
  }
}