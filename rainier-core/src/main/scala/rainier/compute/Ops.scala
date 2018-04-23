package rainier.compute

private sealed trait BinaryOp {
  def apply(left: Double, right: Double): Double
}

private sealed trait CommutativeOp extends BinaryOp

private case object AddOp extends CommutativeOp {
  def apply(left: Double, right: Double) = left + right
}

private case object MultiplyOp extends CommutativeOp {
  def apply(left: Double, right: Double) = left * right
}

private case object SubtractOp extends BinaryOp {
  def apply(left: Double, right: Double) = left - right
}

private case object DivideOp extends BinaryOp {
  def apply(left: Double, right: Double) = left / right
}

private case object OrOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (left == 0.0)
      right
    else
      left
}

private case object AndOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (right == 0.0)
      0.0
    else
      left
}

private case object AndNotOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (right == 0.0)
      left
    else
      0.0
}

private sealed trait UnaryOp {
  def apply(original: Double): Double
}

private case object ExpOp extends UnaryOp {
  def apply(original: Double) = math.exp(original)
}

private case object LogOp extends UnaryOp {
  def apply(original: Double) = math.log(original)
}

private case object AbsOp extends UnaryOp {
  def apply(original: Double) = original.abs
}
