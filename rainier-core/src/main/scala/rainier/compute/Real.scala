package rainier.compute

sealed trait Real {
  def +(other: Real): Real = BinaryReal(this, other, AddOp)
  def *(other: Real): Real = BinaryReal(this, other, MultiplyOp)
  def -(other: Real): Real = BinaryReal(this, other, SubtractOp)
  def /(other: Real): Real = BinaryReal(this, other, DivideOp)
  def ||(other: Real): Real = BinaryReal(this, other, OrOp)
  def &&(other: Real): Real = BinaryReal(this, other, AndOp)
  def &&!(other: Real): Real = BinaryReal(this, other, AndNotOp)
  def exp: Real = UnaryReal(this, ExpOp)
  def log: Real = UnaryReal(this, LogOp)
  def abs: Real = UnaryReal(this, AbsOp)
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

  def variables(real: Real): Set[Variable] = {
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

  var prune = true
  var intern = true
  def optimize(real: Real): Real = {
    var result = real
    if (prune)
      result = Pruner.prune(result)
    if (intern)
      result = Table.intern(result)
    result
  }

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

case class Constant(value: Double) extends Real

class BinaryReal(val left: Real, val right: Real, val op: BinaryOp) extends Real
object BinaryReal {
  def apply(left: Real, right: Real, op: BinaryOp): Real =
    Real.optimize(new BinaryReal(left, right, op))
}

class UnaryReal(val original: Real, val op: UnaryOp) extends Real
object UnaryReal {
  def apply(original: Real, op: UnaryOp): Real =
    Real.optimize(new UnaryReal(original, op))
}

class Variable extends Real

sealed trait BinaryOp {
  def apply(left: Double, right: Double): Double
}

sealed trait CommutativeOp extends BinaryOp

case object AddOp extends CommutativeOp {
  def apply(left: Double, right: Double) = left + right
}

case object MultiplyOp extends CommutativeOp {
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
