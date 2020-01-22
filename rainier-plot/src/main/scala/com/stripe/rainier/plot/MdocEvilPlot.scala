package com.stripe.rainier.plot

import com.cibo.evilplot.geometry.Drawable
import java.nio.file.Files
import java.nio.file.Paths
import mdoc._
import scala.meta.inputs.Position

class EvilplotModifier extends PostModifier {
  val name = "evilplot"
  def process(ctx: PostModifierContext): String = {
    val relpath = Paths.get(ctx.info)
    val out = ctx.outputFile.toNIO.getParent.resolve(relpath)
    ctx.lastValue match {
      case d: Drawable =>
        Files.createDirectories(out.getParent)
        if (!Files.isRegularFile(out)) {
          d.write(out.toFile)
        }
        s"![](${ctx.info})"
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
  expected: com.cibo.evilplot.geometry.Drawable
  obtained: $obtained"""
        )
        ""
    }
  }
}
