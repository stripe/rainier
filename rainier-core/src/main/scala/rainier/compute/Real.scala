package rainier.compute

sealed trait Real {
  def +(other: Real): Real = other match {
    case Constant(0.0) => this
    case Constant(v) => Line(Map(this -> 1.0), v)
    case _ => Line(Map(this -> 1.0, other -> 1.0), 0.0)
  }
    
  def *(other: Real): Real = other match {
    case Constant(1.0) => this
    case Constant(v) => Line(Map(this -> v), 0.0)
    case _ => Multiply(this, other)
  }

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = this * other.reciprocal

  def exp: Real = unary(ExpOp)
  def log: Real = unary(LogOp)
  def abs: Real = unary(AbsOp)
  def reciprocal: Real = unary(RecipOp)

  protected def unary(op: UnaryOp) = UnaryReal(this, op)

  def >(other: Real): Real = Real.isPositive(this - other)
  def <(other: Real): Real = Real.isNegative(this - other)
  def >=(other: Real): Real = Real.one - (this < other)
  def <=(other: Real): Real = Real.one - (this > other)

  lazy val variables: Seq[Variable] = Real.variables(this).toList
  def gradient: Seq[Real] = Gradient.derive(variables, this)
}

object Real {
  import scala.language.implicitConversions

  implicit def apply[N](value: N)(implicit toReal: ToReal[N]): Real =
    toReal(value)
  def seq[A](as: Seq[A])(implicit toReal: ToReal[A]): Seq[Real] =
    as.map(toReal(_))

  def sum(seq: Seq[Real]): Real =
    if (seq.isEmpty)
      Real.zero
    else
      reduceCommutative(seq, AddOp)

  def product(seq: Seq[Real]): Real =
    if (seq.isEmpty)
      Real.one
    else
      reduceCommutative(seq, MultiplyOp)

  def logSumExp(seq: Seq[Real]): Real =
    sum(seq.map(_.exp)).log //TODO: special case this
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
        case Constant(_)   => acc
        case b: BinaryReal => loop(b.right, loop(b.left, acc))
        case u: UnaryReal  => loop(u.original, acc)
        case v: Variable   => acc + v
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }

  def print(real: Real, depth: Int = 0): Unit = {
    val padding = "  " * depth
    real match {
      case Constant(v) => println(padding + v)
      case b: BinaryReal =>
        println(padding + b.op)
        print(b.left, depth + 1)
        print(b.right, depth + 1)
      case u: UnaryReal =>
        println(padding + u.op)
        print(u.original, depth + 1)
      case v: Variable =>
        println(padding + v)
      case If(test, nz, z) =>
        println(padding + "If ")
        print(test, depth + 1)
        print(nz, depth + 1)
        print(z, depth + 1)
    }
  }

  private[compute] def optimize(real: Real): Real =
    Table.intern(Pruner.prune(real))

  private def reduceCommutative(seq: Seq[Real], op: CommutativeOp): Real =
    if (seq.size == 1)
      seq.head
    else
      reduceCommutative(seq.grouped(2).toList.map {
        case oneOrTwo =>
          if (oneOrTwo.size == 1)
            oneOrTwo.head
          else
            BinaryReal(oneOrTwo(0), oneOrTwo(1), op)
      }, op)
}

class Variable extends Real

case class If private (test: Real, whenNonZero: Real, whenZero: Real)
    extends Real
object If {
  def apply(test: Real, whenNonZero: Real, whenZero: Real): Real =
    Real.optimize(new If(test, whenNonZero, whenZero))
}

private case class Constant(value: Double) extends Real {
  override def +(other: Real): Real = other match {
    case Constant(v) => Constant(value + v)
    case l: Line     => l + this
    case _           => new Line(Map(other -> 1.0), value)
  }

  override def *(other: Real): Real = other match {
    case Constant(v) => Constant(value + v)
    case l: Line     => l * this
    case _           => new Line(Map(other -> value), 0.0)
  }

  override protected def unary(op: UnaryOp) = Constant(op(value))
}

private class UnaryReal private (val original: Real, val op: UnaryOp)
    extends Real {

  override protected def unary(nextOp: UnaryOp): Real =
    (op, nextOp) match {
      case (LogOp,ExpOp) => original
      case (ExpOp, LogOp) => original
      case (RecipOp, RecipOp) => original
      case (AbsOp, AbsOp) => this
      //1/e^x => e^-x??
      case _ => super.unary(nextOp)
    }
  }
}

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
//  def log: Real = if a > 0 && b == 0, log(a) + log(x)
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
