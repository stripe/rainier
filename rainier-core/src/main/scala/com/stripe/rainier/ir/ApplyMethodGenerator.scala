package com.stripe.rainier.ir

final private case class ApplyMethodGenerator(classPrefix: String,
                                              classSizeLimit: Int,
                                              outputMethods: Seq[Int])
    extends MethodGenerator {
  val isStatic: Boolean = false
  val methodName: String = "output"
  val methodDesc: String = "([D[DI)D"

  loadOutputIndex()
  tableSwitch(outputMethods) {
    case Some(m) => callExprMethod(m)
    case None    => constant(0.0)
  }
  returnDouble()
}
