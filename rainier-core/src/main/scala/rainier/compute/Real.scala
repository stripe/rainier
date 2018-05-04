package rainier.compute

sealed trait Real {
  def +(other: Real): Real = RealOps.add(this, other)
  def *(other: Real): Real = RealOps.multiply(this, other)

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.pow(-1)

  def pow[N](exponent: N)(implicit num: Numeric[N]): Real =
    RealOps.pow(this, num.toDouble(exponent))

  def exp: Real = RealOps.unary(this, ExpOp)
  def log: Real = RealOps.unary(this, LogOp)
  def abs: Real = RealOps.unary(this, AbsOp)

  def >(other: Real): Real = RealOps.isPositive(this - other)
  def <(other: Real): Real = RealOps.isNegative(this - other)
  def >=(other: Real): Real = Real.one - (this < other)
  def <=(other: Real): Real = Real.one - (this > other)

  lazy val variables: Seq[Variable] = RealOps.variables(this).toList
  def gradient: Seq[Real] = Gradient.derive(variables, this)
}

object Real {
  implicit def apply[N](value: N)(implicit toReal: ToReal[N]): Real =
    toReal(value)
  def seq[A](as: Seq[A])(implicit toReal: ToReal[A]): Seq[Real] =
    as.map(toReal(_))

  def sum(seq: Seq[Real]): Real =
    seq.foldLeft(Real.zero)(_ + _)

  val zero: Real = Real(0.0)
  val one: Real = Real(1.0)
}

private case class Constant(value: Double) extends Real

sealed trait NonConstant extends Real

class Variable extends NonConstant

private case class Unary(original: NonConstant, op: UnaryOp) extends NonConstant

private sealed trait UnaryOp
private case object ExpOp extends UnaryOp
private case object LogOp extends UnaryOp
private case object AbsOp extends UnaryOp

private class Line(val ax: Map[NonConstant, Double], val b: Double)
    extends NonConstant

private case class LogLine(ax: Map[NonConstant, Double]) extends NonConstant

case class If private (test: NonConstant, whenNonZero: Real, whenZero: Real)
    extends NonConstant

object If {
  def apply(test: Real, whenNonZero: Real, whenZero: Real): Real =
    test match {
      case Constant(0.0)   => whenZero
      case Constant(v)     => whenNonZero
      case nc: NonConstant => new If(nc, whenNonZero, whenZero)
    }
}
