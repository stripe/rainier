package com.stripe.rainier.compute

import com.stripe.rainier.ir

/*
A Real is a DAG which represents a mathematical function
from 0 or more real-valued input parameters to a single real-valued output.

You can create new single-node DAGs either like `Real(2.0)`, for a constant,
or with `new Variable` to introduce an input parameter. You can create more interesting DAGs
by combining Reals using the standard mathematical operators, eg `(new Variable) + Real(2.0)`
is the function f(x) = x+2, or `(new Variable) * (new Variable).log` is the function f(x,y) = xlog(y).
Every such operation on Real results in a new Real.
Apart from Variable and a simple ternary If expression, all of the subtypes of Real are private to this package.

You can also automatically derive the gradient of a Real with respect to its variables.
 */
sealed trait Real {
  def +(other: Real): Real = RealOps.add(this, other)
  def *(other: Real): Real = RealOps.multiply(this, other)

  def -(other: Real): Real = this + (other * -1)
  def /(other: Real): Real = RealOps.divide(this, other)

  def min(other: Real): Real = RealOps.min(this, other)
  def max(other: Real): Real = RealOps.max(this, other)

  def pow(exponent: Real): Real = RealOps.pow(this, exponent)

  def exp: Real = RealOps.unary(this, ir.ExpOp)
  def log: Real = RealOps.unary(this, ir.LogOp)

  //because abs a does not have a smooth derivative, try to avoid using it
  def abs: Real = RealOps.unary(this, ir.AbsOp)
  def rectifier: Real = RealOps.unary(this, ir.RectifierOp)

  def >(other: Real): Real = (this - other).rectifier
  def <(other: Real): Real = (other - this).rectifier
  def >=(other: Real): Real = If(this - other, this > other, Real.one)
  def <=(other: Real): Real = If(this - other, this < other, Real.one)

  lazy val variables: List[Variable] = RealOps.variables(this).toList
  lazy val gradient: List[Real] = Gradient.derive(variables, this)
}

object Real {
  implicit def apply[N](value: N)(implicit toReal: ToReal[N]): Real =
    toReal(value)
  def seq[A](as: Seq[A])(implicit toReal: ToReal[A]): Seq[Real] =
    as.map(toReal(_))

  def sum(seq: Iterable[Real]): Real =
    seq.foldLeft(Real.zero)(_ + _)

  def logSumExp(seq: Iterable[Real]): Real = {
    val max = seq.reduce(_ max _)
    val shifted = seq.map { x =>
      x - max
    }
    val summed = Real.sum(shifted.map(_.exp))
    summed.log + max
  }

  //print out Scala code that is equivalent to what the Compiler
  //would produce as JVM bytecode
  def trace(real: Real): Unit = {
    val translator = new Translator
    val irs = List(translator.toIR(real))
    val params = RealOps.variables(real).toList.map(_.param)
    ir.Tracer.trace(params, irs)
  }

  private[compute] val BigZero = BigDecimal(0.0)
  private[compute] val BigOne = BigDecimal(1.0)
  private[compute] val BigTwo = BigDecimal(2.0)
  val zero: Real = Constant(BigZero)
  val one: Real = Constant(BigOne)
  val two: Real = Constant(BigTwo)
  val infinity: Real = Infinity
  val negInfinity: Real = NegInfinity
}

final private case class Constant(value: BigDecimal) extends Real
final private object Infinity extends Real
final private object NegInfinity extends Real

sealed trait NonConstant extends Real

final class Variable extends NonConstant {
  private[compute] val param = new ir.Parameter(0)
}

final private case class Unary(original: NonConstant, op: ir.UnaryOp)
    extends NonConstant

/*
This node type represents any linear transformation from an input vector to an output
scalar as the function `ax + b`, where x is the input vector, a is a constant vector, ax is their dot product,
and b is a constant scalar.

This is used to represent all additions and any multiplications by constants.

Because it is common for ax to have a large number of terms, this is deliberately not a case class,
as equality comparisons would be too expensive. The impact of this is subtle, see [0] at the bottom of this file
for an example.
 */
private final class Line private (val ax: Map[NonConstant, BigDecimal],
                                  val b: BigDecimal)
    extends NonConstant

private object Line {
  def apply(ax: Map[NonConstant, BigDecimal], b: BigDecimal): Line = {
    require(ax.size > 0)
    new Line(ax, b)
  }

  def apply(nc: NonConstant): Line =
    nc match {
      case l: Line => l
      case l: LogLine =>
        LogLineOps
          .distribute(l)
          .getOrElse(Line(Map(l -> Real.BigOne), Real.BigZero))
      case _ => Line(Map(nc -> Real.BigOne), Real.BigZero)
    }
}

/*
This node type represents non-linear transformations from an input vector to a scalar,
of the form `x^a * y^b * z^c ...` where x,y,z are the elements of the input vector,
and a,b,c are constant exponents.

Unlike for Line, it is not expected that ax will have a large number of terms, and performance will suffer if it does.
Luckily, this aligns well with the demands of numerical stability: if you have to multiply a lot of numbers
together, you are better off adding their logs.
 */
private final case class LogLine(
    ax: Map[NonConstant, BigDecimal]
) extends NonConstant {
  require(ax.size > 0)
}

private object LogLine {
  def apply(nc: NonConstant): LogLine =
    nc match {
      case l: LogLine => l
      case _          => LogLine(Map(nc -> Real.BigOne))
    }
}

/*
This node type represents an expression which is equal to `whenZero` when
test is equal to zero, and `whenNotZero` otherwise. Because this expression
does not have a smooth derivative, it is not recommended that you use this
unless absolutely necessary.
 */
final case class If private (test: NonConstant,
                             whenNonZero: Real,
                             whenZero: Real)
    extends NonConstant

object If {
  def apply(test: Real, whenNonZero: => Real, whenZero: => Real): Real =
    test match {
      case Constant(Real.BigZero)               => whenZero
      case Constant(_) | Infinity | NegInfinity => whenNonZero
      case nc: NonConstant                      => new If(nc, whenNonZero, whenZero)
    }
}

private final case class Pow private (base: Real, exponent: NonConstant)
    extends NonConstant

/*
[0] For example, of the following four ways of computing the same result, only the first two will have the most efficient
representation:

//#1
(x+y+3).pow(2)

//#2
val z = x+y+3
z*z

//#3
(x+y+3)*(x+y+3)

//#4
(x+y+3)*(y+x+3)

In the second case, because z == z, the multiplication can be collapsed into an exponent. In the third and
fourth cases, although the expressions are equivalent, the objects are not equal, and so this will not happen.
However, in the third case, at the compilation stage the common sub-expressions will still be recognized and so there
will not be any double computation. In the fourth case, because of the reordering, this won't happen, and so
`x+y+3` will be computed twice (in two different orders).
 */
