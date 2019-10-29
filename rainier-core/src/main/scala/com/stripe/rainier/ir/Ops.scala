package com.stripe.rainier.ir

sealed trait BinaryOp extends Product with Serializable {
  def isCommutative: Boolean
}
final case object AddOp extends BinaryOp {
  val isCommutative: Boolean = true
}
final case object MultiplyOp extends BinaryOp {
  val isCommutative: Boolean = true
}
final case object SubtractOp extends BinaryOp {
  val isCommutative: Boolean = false
}
final case object DivideOp extends BinaryOp {
  val isCommutative: Boolean = false
}
final case object PowOp extends BinaryOp {
  val isCommutative: Boolean = false
}
final case object CompareOp extends BinaryOp {
  val isCommutative: Boolean = false
}

sealed trait UnaryOp extends Product with Serializable
final case object ExpOp extends UnaryOp
final case object LogOp extends UnaryOp
final case object AbsOp extends UnaryOp
final case object NoOp extends UnaryOp

final case object SinOp extends UnaryOp
final case object CosOp extends UnaryOp
final case object TanOp extends UnaryOp

final case object AsinOp extends UnaryOp
final case object AcosOp extends UnaryOp
final case object AtanOp extends UnaryOp
