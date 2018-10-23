package com.stripe.rainier.ir

final private case class OutputMethodGenerator(classSizeLimit: Int,
                                               outputIDs: Seq[(String, Int)])
    extends MethodGenerator {
  val isStatic: Boolean = false
  val methodName: String = "output"
  val methodDesc: String = "([D[DI)D"

  loadOutputIndex()
  tableSwitch(outputIDs) {
    case Some((c, i)) => callExprMethod(c, i)
    case None         => throwNPE() //easiest exception to generate
  }
  returnDouble()
}
