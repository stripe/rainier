package com.stripe.rainier.notebook

import mdoc._

class PrettyPrintModifier extends PostModifier {
  val name = "pprint"
  val pprint = PPrint.pprint()
  def process(ctx: PostModifierContext): String = {
    val variables = ctx.variables
      .map { v =>
        val pp = pprint(v.runtimeValue)
        val truncated =
          if (pp.plainText.split("\n").size > 5) {
            val keep = pp.plainText.split("\n").take(5).mkString("\n").size
            pp.substring(0, keep) + "\n..."
          } else
            pp
        s"${v.name}: ${v.staticType} = $truncated"
      }
      .mkString("\n")
    s"```scala \n${ctx.originalCode.text}\n\n/*\n$variables\n*/\n```"
  }
}
