package rainier.compute

sealed trait Real {
  def +[N](other: N)(implicit ev: ToReal[N]): Real =
    BinaryReal(this, ev(other), AddOp)
  def *[N](other: N)(implicit ev: ToReal[N]): Real =
    BinaryReal(this, ev(other), MultiplyOp)
  def -[N](other: N)(implicit ev: ToReal[N]): Real =
    BinaryReal(this, ev(other), SubtractOp)
  def /[N](other: N)(implicit ev: ToReal[N]): Real =
    BinaryReal(this, ev(other), DivideOp)
  def log: Real = UnaryReal(this, LogOp)
  def exp: Real_+ = Unsigned(UnaryReal(this, ExpOp))
  def abs: Real_+ = Unsigned(UnaryReal(this, AbsOp))

  lazy val variables: Seq[Variable] = Real.variables(this).toList
  def gradient: Seq[Real] = Gradient.derive(variables, this)

  private[compute] def signed: Signed
}

object Real {
  private[compute] implicit def signed(real: Real): Signed = real.signed

  def apply[N](n: N)(implicit num: Numeric[N]): Real =
    Constant(num.toDouble(n))

  def sum[N](seq: Seq[N])(implicit ev: ToReal[N]): Real =
    if (seq.isEmpty)
      Real.zero
    else
      reduceCommutative(seq.map { n =>
        ev(n).signed
      }, AddOp)

  def product[N](seq: Seq[N])(implicit ev: ToReal[N]): Real =
    if (seq.isEmpty)
      Real.one
    else
      reduceCommutative(seq.map { n =>
        ev(n).signed
      }, MultiplyOp)

  def logSumExp[N](seq: Seq[N])(implicit ev: ToReal[N]): Real =
    sum(seq.map { n =>
      ev(n).exp
    }).log //TODO: special case this

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

private[compute] sealed trait Signed extends Real {
  private[compute] def signed = this
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
    require(v > 0.0)
    Unsigned(Constant(v))
  }
}

private case class Unsigned(signed: Signed) extends Real_+
