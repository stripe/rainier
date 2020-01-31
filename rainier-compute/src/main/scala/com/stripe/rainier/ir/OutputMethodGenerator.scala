package com.stripe.rainier.ir

final private case class OutputMethodGenerator(methodNum: Int,
                                               classSizeLimit: Int,
                                               outputIDs: Seq[(String, Int)])
    extends MethodGenerator {
  val isStatic: Boolean = false
  val methodName: String = s"output$methodNum"
  val methodDesc: String = "([D[DI)D"

  if (outputIDs.isEmpty)
    constant(0.0)
  else {
    loadOutputIndex()
    tableSwitch(outputIDs, 0) {
      case Some((c, i)) => callExprMethod(c, i)
      case None         => throwNPE() //easiest exception to generate
    }
  }
  returnDouble()
}
