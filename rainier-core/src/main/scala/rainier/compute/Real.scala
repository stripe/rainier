package rainier.compute

sealed trait Real {
  def +(other: Real): Real
  def *(other: Real): Real

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.pow(-1)

  def pow(power: Real): Real = Pow(this, power)

  def exp: Real = unary(ExpOp)
  def log: Real = unary(LogOp)
  def abs: Real = unary(AbsOp)

  private[compute] def unary(op: UnaryOp): Real

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
        case Constant(_) => acc
        case p: Product  => loop(p.right, loop(p.left, acc))
        case w: Pow      => loop(w.original, loop(w.exponent, acc))
        case u: Unary    => loop(u.original, acc)
        case v: Variable => acc + v
        case l: Line     => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }
}

sealed trait NonConstant extends Real {
  def +(other: Real) = other match {
    case Constant(0.0) => this
    case Constant(v)   => Line(Map(this -> 1.0), v)
    case nc: NonConstant =>
      Line(Map(this -> 1.0, nc -> 1.0), 0.0)
  }

  def *(other: Real): Real = other match {
    case Constant(1.0)   => this
    case Constant(0.0)   => Real.zero
    case Constant(v)     => Line(Map(this -> v), 0.0)
    case nc: NonConstant => Product(this, nc)
  }

  private[compute] def unary(op: UnaryOp): Real = Unary(this, op)
}

class Variable extends NonConstant

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

private case class Constant(value: Double) extends Real {
  def +(other: Real): Real = other match {
    case Constant(v)     => Constant(value + v)
    case nc: NonConstant => nc + this
  }

  def *(other: Real): Real = other match {
    case Constant(v)     => Constant(value * v)
    case nc: NonConstant => nc * this
  }

  private[compute] def unary(op: UnaryOp): Real = op match {
    case ExpOp => Constant(Math.exp(value))
    case LogOp => Constant(Math.log(value))
    case AbsOp => Constant(Math.abs(value))
  }
}

private case class Unary private (original: NonConstant, op: UnaryOp)
    extends NonConstant
private object Unary {
  def apply(original: NonConstant, op: UnaryOp): Real =
    Optimizer(new Unary(original, op))
}

private case class Pow private (original: Real, exponent: Real)
    extends NonConstant
private object Pow {
  def apply(original: Real, exponent: Real): Real =
    Optimizer(new Pow(original, exponent))
}

private class Product private (val left: NonConstant, val right: NonConstant)
    extends NonConstant
private object Product {
  def apply(left: NonConstant, right: NonConstant): Real =
    Optimizer(new Product(left, right))
}

private class Line private (val ax: Map[NonConstant, Double], val b: Double)
    extends NonConstant
private object Line {
  def apply(ax: Map[NonConstant, Double], b: Double): Real =
    Optimizer(new Line(ax, b))
}

private sealed trait UnaryOp
private case object ExpOp extends UnaryOp
private case object LogOp extends UnaryOp
private case object AbsOp extends UnaryOp
