package rainier.compute

sealed trait Real {
  def +(other: Real): Real = Pruner.prune(new BinaryReal(this, other, AddOp))
  def *(other: Real): Real =
    Pruner.prune(new BinaryReal(this, other, MultiplyOp))
  def -(other: Real): Real =
    Pruner.prune(new BinaryReal(this, other, SubtractOp))
  def /(other: Real): Real = Pruner.prune(new BinaryReal(this, other, DivideOp))
  def ||(other: Real): Real = Pruner.prune(new BinaryReal(this, other, OrOp))
  def &&(other: Real): Real = Pruner.prune(new BinaryReal(this, other, AndOp))
  def &&!(other: Real): Real =
    Pruner.prune(new BinaryReal(this, other, AndNotOp))
  def exp: Real = Pruner.prune(new UnaryReal(this, ExpOp))
  def log: Real = Pruner.prune(new UnaryReal(this, LogOp))
  def abs: Real = Pruner.prune(new UnaryReal(this, AbsOp))
}

object Real {
  import scala.language.implicitConversions

  implicit def apply[N](value: N)(implicit toReal: ToReal[N]): Real =
    toReal(value)
  def seq[A](as: Seq[A])(implicit toReal: ToReal[A]): Seq[Real] =
    as.map(toReal(_))
  def sum(seq: Seq[Real]): Real = Pruner.prune(new SumReal(seq))
  def logSumExp(seq: Seq[Real]): Real =
    sum(seq.map(_.exp)).log //TODO: special case this
  val zero: Real = Real(0.0)
  val one: Real = Real(1.0)

  def variables(real: Real): Set[Variable] = {
    def loop(r: Real, acc: Set[Variable]): Set[Variable] =
      r match {
        case Constant(_)   => acc
        case b: BinaryReal => loop(b.right, loop(b.left, acc))
        case u: UnaryReal  => loop(u.original, acc)
        case s: SumReal    => s.seq.foldLeft(acc) { case (l, v) => loop(v, l) }
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
      case s: SumReal =>
        println(padding + "Sum{" + s.seq.size + "}")
        print(s.seq.head, depth + 1)
      case v: Variable =>
        println(padding + v)
    }
  }
}

case class Constant(value: Double) extends Real
class BinaryReal(val left: Real, val right: Real, val op: BinaryOp) extends Real
class UnaryReal(val original: Real, val op: UnaryOp) extends Real
class SumReal(val seq: Seq[Real]) extends Real
class Variable extends Real

sealed trait BinaryOp {
  def apply(left: Double, right: Double): Double
}

case object AddOp extends BinaryOp {
  def apply(left: Double, right: Double) = left + right
}

case object MultiplyOp extends BinaryOp {
  def apply(left: Double, right: Double) = left * right
}

case object SubtractOp extends BinaryOp {
  def apply(left: Double, right: Double) = left - right
}

case object DivideOp extends BinaryOp {
  def apply(left: Double, right: Double) = left / right
}

case object OrOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (left == 0.0)
      right
    else
      left
}

case object AndOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (right == 0.0)
      0.0
    else
      left
}

case object AndNotOp extends BinaryOp {
  def apply(left: Double, right: Double) =
    if (right == 0.0)
      left
    else
      0.0
}

sealed trait UnaryOp {
  def apply(original: Double): Double
}

case object ExpOp extends UnaryOp {
  def apply(original: Double) = math.exp(original)
}

case object LogOp extends UnaryOp {
  def apply(original: Double) = math.log(original)
}

case object AbsOp extends UnaryOp {
  def apply(original: Double) = original.abs
}
