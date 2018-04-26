package rainier.compute

sealed trait Real {
  def +(other: Real): Real = BinaryReal(this, other, AddOp)
  def *(other: Real): Real = BinaryReal(this, other, MultiplyOp)
  def -(other: Real): Real = BinaryReal(this, other, SubtractOp)
  def /(other: Real): Real = BinaryReal(this, other, DivideOp)

  def exp: Real = UnaryReal(this, ExpOp)
  def log: Real = UnaryReal(this, LogOp)
  def abs: Real = UnaryReal(this, AbsOp)

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
    nonZeroIsPositive(BinaryReal(real, -1, OrOp))

  private def isNegative(real: Real): Real =
    Real.one - nonZeroIsPositive(BinaryReal(real, 1, OrOp))

  private def variables(real: Real): Set[Variable] = {
    def loop(r: Real, acc: Set[Variable]): Set[Variable] =
      r match {
        case Constant(_)   => acc
        case b: BinaryReal => loop(b.right, loop(b.left, acc))
        case u: UnaryReal  => loop(u.original, acc)
        case v: Variable   => acc + v
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

private case class Constant(value: Double) extends Real

private class BinaryReal private (val left: Real,
                                  val right: Real,
                                  val op: BinaryOp)
    extends Real

private object BinaryReal {
  def apply(left: Real, right: Real, op: BinaryOp): Real =
    Real.optimize(new BinaryReal(left, right, op))
}

private class UnaryReal private (val original: Real, val op: UnaryOp)
    extends Real
private object UnaryReal {
  def apply(original: Real, op: UnaryOp): Real =
    Real.optimize(new UnaryReal(original, op))
}

class Variable extends Real

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
