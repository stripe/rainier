package com.stripe.rainier.ir

final private case class ApplyMethodGenerator(className: String,
                                              outputMethods: Seq[Int])
    extends MethodGenerator {
  val isPrivate: Boolean = false
  val methodName: String = "apply"
  val methodDesc: String = "([D[D[D)V"

  outputMethods.zipWithIndex.foreach {
    case (m, i) =>
      storeOutput(i) { callExprMethod(m) }
  }
  returnVoid()
}
