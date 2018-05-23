package com.stripe.rainier.ir

final private case class ApplyMethodGenerator(className: String,
                                              outputMethods: Seq[Int],
                                              numGlobals: Int)
    extends MethodGenerator {
  val isPrivate: Boolean = false
  val methodName: String = "apply"
  val methodDesc: String = "([D)[D"

  newArrayOfSize(numGlobals)
  storeGlobalVars()
  newArray(outputMethods) { n =>
    callExprMethod(n)
  }
  returnArray()
}
