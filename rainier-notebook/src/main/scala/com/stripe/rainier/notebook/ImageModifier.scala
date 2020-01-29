package com.stripe.rainier.notebook

import almond.display.Image
import java.nio.file.{Files, Paths, StandardOpenOption}
import mdoc._
import scala.meta.inputs.Position

class ImageModifier extends PostModifier {
  val name = "image"
  def process(ctx: PostModifierContext): String = {
    val relpath = Paths.get(ctx.info)
    val out = ctx.outputFile.toNIO.getParent.resolve(relpath)
    ctx.lastValue match {
      case img: Image => {
        Files.createDirectories(out.getParent)
        Files.write(out, img.byteArrayOpt.get, StandardOpenOption.CREATE)
        s"![](${ctx.info})"
      }
      case _ =>
        val (pos, obtained) = ctx.variables.lastOption match {
          case Some(variable) =>
            val prettyObtained =
              s"${variable.staticType} = ${variable.runtimeValue}"
            (variable.pos, prettyObtained)
          case None =>
            (Position.Range(ctx.originalCode, 0, 0), "nothing")
        }
        ctx.reporter.error(
          pos,
          s"""type mismatch:
  expected: almond.display.Image
  obtained: $obtained"""
        )
        ""
    }
  }
}
