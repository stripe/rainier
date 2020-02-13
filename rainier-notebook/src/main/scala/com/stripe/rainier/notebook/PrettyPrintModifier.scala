package com.stripe.rainier.notebook

import mdoc._

class PrettyPrintModifier extends PostModifier {
  val name = "pprint"
  val pprint = PrettyPrint.pprint()
  def process(ctx: PostModifierContext): String = {
    val variables = ctx.variables
      .map { v =>
        val pp = pprint(v.runtimeValue)
        s"${v.name}: ${v.staticType} = $pp"
      }
      .mkString("\n")
    s"```scala \n${ctx.originalCode.text}\n\n/*\n$variables\n*/\n```"
  }
}
