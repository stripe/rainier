package com.stripe.rainier.ir

final private case class OutputMethodGenerator(classPrefix: String,
                                               classSizeLimit: Int,
                                               outputMethods: Seq[Int])
    extends MethodGenerator {
  val isStatic: Boolean = false
  val methodName: String = "output"
  val methodDesc: String = "([D[DI)D"

  loadOutputIndex()
  tableSwitch(outputMethods) {
    case Some(m) => callExprMethod(m)
    case None    => throwNPE() //easiest exception to generate
  }
  returnDouble()
}
