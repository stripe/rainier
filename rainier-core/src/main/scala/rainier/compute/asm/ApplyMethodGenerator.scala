package rainier.compute.asm

import rainier.compute._

private case class ApplyMethodGenerator(className: String,
                                        outputMethods: Seq[Int],
                                        numGlobals: Int)
    extends MethodGenerator {
  val isPrivate = false
  val methodName = "apply"
  val methodDesc = "([D)[D"

  newArrayOfSize(numGlobals)
  storeGlobalVars()
  newArray(outputMethods) { n =>
    callExprMethod(n)
  }
  ret()
}
