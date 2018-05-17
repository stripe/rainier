package com.stripe.rainier.ir

sealed trait BinaryOp {
  def isCommutative: Boolean
}
object AddOp extends BinaryOp {
  val isCommutative = true
}
object MultiplyOp extends BinaryOp {
  val isCommutative = true
}
object SubtractOp extends BinaryOp {
  val isCommutative = false
}
object DivideOp extends BinaryOp {
  val isCommutative = false
}
object PowOp extends BinaryOp {
  val isCommutative = false
}

sealed trait UnaryOp
case object ExpOp extends UnaryOp
case object LogOp extends UnaryOp
case object AbsOp extends UnaryOp
