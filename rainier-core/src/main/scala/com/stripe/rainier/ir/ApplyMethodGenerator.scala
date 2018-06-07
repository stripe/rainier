package com.stripe.rainier.ir

final private case class ApplyMethodGenerator(classPrefix: String,
                                              classSizeLimit: Int,
                                              outputMethods: Seq[Int])
    extends MethodGenerator {
  val isStatic: Boolean = false
  val methodName: String = "apply"
  val methodDesc: String = "([D[D[D)V"

  outputMethods.zipWithIndex.foreach {
    case (m, i) =>
      storeOutput(i) { callExprMethod(m) }
  }
  returnVoid()
}
