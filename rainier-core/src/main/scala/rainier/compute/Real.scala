package rainier.compute

sealed trait Real {
  def +(other: Real): Real = other match {
    case Constant(0.0) => this
    case Constant(v)   => new Line(Map(this -> 1.0), v)
    case _             => new Line(Map(this -> 1.0, other -> 1.0), 0.0)
  }

  def *(other: Real): Real = other match {
    case Constant(1.0) => this
    case Constant(0.0) => Real.zero
    case Constant(v)   => new Line(Map(this -> v), 0.0)
    case _             => new Product(this, other)
  }

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.reciprocal

  def exp: Real = unary(ExpOp)
  def log: Real = unary(LogOp)
  def abs: Real = unary(AbsOp)
  def reciprocal: Real = unary(RecipOp)

  protected def unary(op: UnaryOp): Real = new UnaryReal(this, op)

  def >(other: Real): Real = Real.isPositive(this - other)
  def <(other: Real): Real = Real.isNegative(this - other)
  def >=(other: Real): Real = Real.one - (this < other)
  def <=(other: Real): Real = Real.one - (this > other)

  lazy val variables: Seq[Variable] = Real.variables(this).toList
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

  private def nonZeroIsPositive(real: Real): Real =
    ((real.abs / real) + 1) / 2

  private def isPositive(real: Real): Real =
    If(real, nonZeroIsPositive(real), Real.zero)

  private def isNegative(real: Real): Real =
    If(real, Real.one - nonZeroIsPositive(real), Real.zero)

  private def variables(real: Real): Set[Variable] = {
    def loop(r: Real, acc: Set[Variable]): Set[Variable] =
      r match {
        case Constant(_)  => acc
        case p: Product   => loop(p.right, loop(p.left, acc))
        case u: UnaryReal => loop(u.original, acc)
        case v: Variable  => acc + v
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }
}

class Variable extends Real

case class If private (test: Real, whenNonZero: Real, whenZero: Real)
    extends Real

object If {
  def apply(test: Real, whenNonZero: Real, whenZero: Real): Real =
    test match {
      case Constant(0.0) => whenZero
      case Constant(v)   => whenNonZero
      case _             => new If(test, whenNonZero, whenZero)
    }
}

private case class Constant(value: Double) extends Real {
  override def +(other: Real): Real = other match {
    case Constant(v) => Constant(value + v)
    case _           => other + this
  }

  override def *(other: Real): Real = other match {
    case Constant(v) => Constant(value * v)
    case _           => other * this
  }

  override protected def unary(op: UnaryOp) = Constant(op(value))
}

private class UnaryReal(val original: Real, val op: UnaryOp) extends Real {

  override protected def unary(nextOp: UnaryOp): Real =
    (op, nextOp) match {
      case (LogOp, ExpOp)     => original
      case (ExpOp, LogOp)     => original
      case (RecipOp, RecipOp) => original
      case (AbsOp, AbsOp)     => this
      //1/e^x => e^-x??
      case _ => super.unary(nextOp)
    }
}

private class Product(val left: Real, val right: Real) extends Real

private class Line(val ax: Map[Real, Double], val b: Double) extends Real {
  override def +(other: Real): Real = other match {
    case Constant(v) => new Line(ax, b + v)
    case l: Line     => new Line(merge(ax, l.ax), l.b + b)
    case _           => new Line(ax + (other -> 1.0), b)
  }
  def *(other: Real): Real = other match {
    case Constant(v) => new Line(ax.map { case (r, d) => r -> d * v }, b * v)
    case _           => super.*(other)
  }

  private def merge(left: Map[Real, Double],
                    right: Map[Real, Double]): Map[Real, Double] = ???
//  def log: Real = if ax.size == 1 && a > 0 && b == 0, log(a) + log(x)
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

private case object RecipOp extends UnaryOp {
  def apply(original: Double) = 1.0 / original
}
