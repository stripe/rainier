package rainier.compute

sealed trait Real {
  def +(other: Real): Real
  def *(other: Real): Real

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.pow(-1)

  def exp: Real = unary(ExpOp)
  def log: Real = unary(LogOp)
  def abs: Real = unary(AbsOp)
  def pow(power: Real): Real

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

  def pow(power: Real) = power match {
    case Constant(1.0) => this
    case Constant(0.0) => Real.one
    case _             => Pow(this, power)
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

  def pow(power: Real): Real =
    power match {
      case Constant(p) => Math.pow(value, p)
      case _           => Pow(this, power)
    }

  private[compute] def unary(op: UnaryOp): Real = op match {
    case ExpOp => Constant(Math.exp(value))
    case LogOp => Constant(Math.log(value))
    case AbsOp => Constant(Math.abs(value))
  }
}

private case class Unary(original: NonConstant, op: UnaryOp)
    extends NonConstant {

  override private[compute] def unary(nextOp: UnaryOp): Real =
    (op, nextOp) match {
      case (LogOp, ExpOp) => original
      case (ExpOp, LogOp) => original
      case (AbsOp, AbsOp) => this
      case _              => super.unary(nextOp)
    }
}

private case class Pow(original: Real, exponent: Real) extends NonConstant {
  override def pow(power: Real): Real = original.pow(exponent * power)
  override def log: Real = original.log * exponent
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
    case Constant(v) => new Line(ax.map { case (r, d) => r -> d * v }, b * v)
    case l: Line =>
      if (ax.size == 1 && l.ax.size == 1) {
        val (x, a) = ax.head
        val (y, c) = l.ax.head
        val d = l.b
        //(ax + b)(cy + d)
        if (x == y) { //acx^2 + (ad+bc)x + bd
          val x2: NonConstant = Pow(x, Constant(2.0))
          val adbc = (a * d) + (b * c)
          val newAx =
            if (adbc == 0.0)
              Map(x2 -> a * c)
            else
              Map(x2 -> a * c, x -> adbc)
          new Line(newAx, b * d)
        } else if (b == 0.0) { //acxy + adx
          val xy: NonConstant = new Product(x, y)
          val ad = a * d
          val newAx =
            if (ad == 0.0)
              Map(xy -> a * c)
            else
              Map(xy -> a * c, x -> ad)
          new Line(newAx, 0.0)
        } else if (d == 0.0) { //acxy + bcy, b != 0
          val xy: NonConstant = new Product(x, y)
          new Line(Map(xy -> a * c, y -> b * c), 0.0)
        } else {
          new Product(this, l)
        }
      } else {
        new Product(this, l)
      }
    case nc: NonConstant => new Product(this, nc)
  }

  override def log: Real = {
    val (c, y) = factor
    c.unary(LogOp) + Math.log(y)
  }

  override def pow(power: Real): Real = {
    val (c, y) = factor
    Pow(c, power) * Constant(y).pow(power)
  }

  def factor: (NonConstant, Double) = {
    if (ax.size == 1 && b == 0)
      (ax.head._1, ax.head._2)
    else {
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

private sealed trait UnaryOp

private case object ExpOp extends UnaryOp
private case object LogOp extends UnaryOp
private case object AbsOp extends UnaryOp
