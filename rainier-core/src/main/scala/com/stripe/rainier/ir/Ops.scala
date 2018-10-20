package com.stripe.rainier.ir

sealed trait BinaryOp {
  def isCommutative: Boolean
}
object AddOp extends BinaryOp {
  val isCommutative: Boolean = true
}
object MultiplyOp extends BinaryOp {
  val isCommutative: Boolean = true
}
object SubtractOp extends BinaryOp {
  val isCommutative: Boolean = false
}
object DivideOp extends BinaryOp {
  val isCommutative: Boolean = false
}
object PowOp extends BinaryOp {
  val isCommutative: Boolean = false
}

sealed trait UnaryOp
case object ExpOp extends UnaryOp
case object LogOp extends UnaryOp
case object AbsOp extends UnaryOp
case object RectifierOp extends UnaryOp
case object NoOp extends UnaryOp
