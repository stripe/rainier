package com.stripe.rainier.notebook

import almond.display.Image
import polynote.runtime._
import java.util.Base64

trait RainierReprsOf[T] extends ReprsOf[T]

object RainierReprsOf {

  def instance[T](reprs: T => Array[ValueRepr]): RainierReprsOf[T] =
    new RainierReprsOf[T] {
      def apply(value: T): Array[ValueRepr] = reprs(value)
    }

  implicit val imageRepr: RainierReprsOf[Image] = instance { img =>
    val imageData = new String(Base64.getEncoder.encode(img.byteArrayOpt.get))
    Array(
      StringRepr("Image"),
      MIMERepr(
        "text/html",
        "<img width=400 height=400 src=\"data:image/png;base64," + imageData + "\" />"))
  }
}
