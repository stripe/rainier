package rainier.compute

sealed trait Real {
  def +(other: Real): Real = BinaryReal(this, other, AddOp)
  def +(other: Double): Real = this + Real(other)
  def *(other: Real): Real = BinaryReal(this, other, MultiplyOp)
  def *(other: Double): Real = this * Real(other)
  def -(other: Real): Real = BinaryReal(this, other, SubtractOp)
  def -(other: Double): Real = this - Real(other)
  def /(other: Real): Real = BinaryReal(this, other, DivideOp)
  def /(other: Double): Real = this / Real(other)
  def log: Real = UnaryReal(this, LogOp)
  def exp: Real_+ = Unsigned(UnaryReal(this, ExpOp))
  def abs: Real_+ = Unsigned(UnaryReal(this, AbsOp))

  lazy val variables: Seq[Variable] = Real.variables(this).toList
  def gradient: Seq[Real] = Gradient.derive(variables, this)

  def signed: Signed
}

object Real {
  implicit def signed(real: Real): Signed = real.signed

  def apply[N](n: N)(implicit num: Numeric[N]): Real =
    Constant(num.toDouble(n))

  def sum(seq: Seq[Real]): Real =
    if (seq.isEmpty)
      Real.zero
    else
      reduceCommutative(seq.map(_.signed), AddOp)

  def product(seq: Seq[Real]): Real =
    if (seq.isEmpty)
      Real.one
    else
      reduceCommutative(seq.map(_.signed), MultiplyOp)

  def logSumExp(seq: Seq[Real]): Real =
    sum(seq.map(_.exp)).log //TODO: special case this

  val zero: Real_+ = Real_+(0.0)
  val one: Real_+ = Real_+(1.0)

  private def variables(real: Signed): Set[Variable] = {
    def loop(r: Signed, acc: Set[Variable]): Set[Variable] =
      r match {
        case Constant(_)   => acc
        case b: BinaryReal => loop(b.right, loop(b.left, acc))
        case u: UnaryReal  => loop(u.original, acc)
        case v: Variable   => acc + v
      }

    loop(real, Set.empty)
  }

  def print(real: Signed, depth: Int = 0): Unit = {
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

  private[compute] def optimize(real: Signed): Real =
    Table.intern(Pruner.prune(real))

  private def reduceCommutative(seq: Seq[Signed], op: CommutativeOp): Real =
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

sealed trait Signed extends Real {
  def signed = this
}

private case class Constant(value: Double) extends Signed

private class BinaryReal private (val left: Signed,
                                  val right: Signed,
                                  val op: BinaryOp)
    extends Signed

private object BinaryReal {
  def apply(left: Signed, right: Signed, op: BinaryOp): Signed =
    Real.optimize(new BinaryReal(left, right, op))
}

private class UnaryReal private (val original: Signed, val op: UnaryOp)
    extends Signed
private object UnaryReal {
  def apply(original: Signed, op: UnaryOp): Real =
    Real.optimize(new UnaryReal(original, op))
}

class Variable extends Signed

sealed trait Real_+ extends Real {
  def +(other: Real_+): Real_+ = Unsigned(signed + other.signed)
  def *(other: Real_+): Real_+ = Unsigned(signed * other.signed)
  def /(other: Real_+): Real_+ = Unsigned(signed / other.signed)
}

object Real_+ {
  def apply[N](n: N)(implicit num: Numeric[N]): Real_+ = {
    val v = num.toDouble(n)
    require(v >= 0.0)
    Unsigned(Constant(v))
  }
}

private case class Unsigned(signed: Signed) extends Real_+

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
