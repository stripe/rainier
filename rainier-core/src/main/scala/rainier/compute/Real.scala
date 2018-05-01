package rainier.compute

sealed trait Real {
  def +(other: Real): Real
  def *(other: Real): Real

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.reciprocal

  def exp: Real = unary(ExpOp)
  def log: Real = unary(LogOp)
  def abs: Real = unary(AbsOp)
  def reciprocal: Real = unary(RecipOp)

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
    case Constant(v)   => new Line(Map(this -> 1.0), v)
    case nc: NonConstant =>
      new Line(Map(this -> 1.0, nc -> 1.0), 0.0)
  }

  def *(other: Real): Real = other match {
    case Constant(1.0)   => this
    case Constant(0.0)   => Real.zero
    case Constant(v)     => new Line(Map(this -> v), 0.0)
    case nc: NonConstant => new Product(this, nc)
  }

  private[compute] def unary(op: UnaryOp): Real = new Unary(this, op)
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

  private[compute] def unary(op: UnaryOp): Real = Constant(op(value))
}

private class Unary(val original: NonConstant, val op: UnaryOp)
    extends NonConstant {

  override private[compute] def unary(nextOp: UnaryOp): Real =
    (op, nextOp) match {
      case (LogOp, ExpOp)     => original
      case (ExpOp, LogOp)     => original
      case (RecipOp, RecipOp) => original
      case (RecipOp, LogOp)   => original.log * -1
      case (AbsOp, AbsOp)     => this
      case _                  => super.unary(nextOp)
    }
}

private class Product(val left: NonConstant, val right: NonConstant)
    extends NonConstant

private class Line(val ax: Map[NonConstant, Double], val b: Double)
    extends NonConstant {
  override def +(other: Real): Real = other match {
    case Constant(v)     => new Line(ax, b + v)
    case l: Line         => new Line(merge(ax, l.ax), l.b + b)
    case nc: NonConstant => new Line(ax + (nc -> 1.0), b)
  }
  override def *(other: Real): Real = other match {
    case Constant(v)     => new Line(ax.map { case (r, d) => r -> d * v }, b * v)
    case nc: NonConstant => new Product(this, nc)
  }

  override def log: Real = {
    val (line, factor) = simplify
    line.unary(LogOp) + Math.log(factor)
  }

  def simplify: (Line, Double) = {
    val mostSimplifyingFactor =
      ax.values
        .groupBy(_.abs)
        .map { case (d, l) => d -> l.size }
        .toList
        .sortBy(_._2)
        .last
        ._1

    val newAx = ax.map {
      case (r, d) => r -> d / mostSimplifyingFactor
    }

    val newB = b / mostSimplifyingFactor

    (new Line(newAx, newB), mostSimplifyingFactor)
  }

  private def merge(
      left: Map[NonConstant, Double],
      right: Map[NonConstant, Double]): Map[NonConstant, Double] = {
    val (big, small) =
      if (left.size > right.size)
        (left, right)
      else
        (right, left)

    small.foldLeft(big) {
      case (acc, (k, v)) =>
        val newV = big
          .get(k)
          .map { bigV =>
            bigV + v
          }
          .getOrElse(v)
        if (newV == 0.0)
          acc - k
        else
          acc + (k -> newV)
    }
  }
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
