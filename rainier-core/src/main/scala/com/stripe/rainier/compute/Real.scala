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

  def dot(left: Iterable[Real], right: Iterable[Real]): Real =
    sum(left.zip(right).map { case (a, b) => a * b })

  def logSumExp(seq: Iterable[Real]): Real = {
    val max = seq.reduce(_ max _)
    val shifted = seq.map { x =>
      x - max
    }
    val summed = Real.sum(shifted.map(_.exp))
    summed.log + max
  }

  def eq(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real =
    lookupCompare(left, right, ifFalse, ifTrue, ifFalse)
  def lt(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real =
    lookupCompare(left, right, ifFalse, ifFalse, ifTrue)
  def gt(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real =
    lookupCompare(left, right, ifTrue, ifFalse, ifFalse)
  def lte(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real =
    lookupCompare(left, right, ifFalse, ifTrue, ifTrue)
  def gte(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real =
    lookupCompare(left, right, ifTrue, ifTrue, ifFalse)

  private def lookupCompare(left: Real,
                            right: Real,
                            gt: Real,
                            eq: Real,
                            lt: Real) =
    Lookup(Compare(left, right), List(lt, eq, gt), -1)

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
  private[compute] val param = new ir.Parameter
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
private final class Line private (val ax: Coefficients, val b: BigDecimal)
    extends NonConstant

private object Line {
  def apply(ax: Coefficients, b: BigDecimal): Line = {
    require(!ax.isEmpty)
    new Line(ax, b)
  }

  def apply(nc: NonConstant): Line =
    nc match {
      case l: Line => l
      case l: LogLine =>
        LogLineOps
          .distribute(l)
          .getOrElse(Line(Coefficients(l -> Real.BigOne), Real.BigZero))
      case _ => Line(Coefficients(nc -> Real.BigOne), Real.BigZero)
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
    ax: Coefficients
) extends NonConstant {
  require(!ax.isEmpty)
}

private object LogLine {
  def apply(nc: NonConstant): LogLine =
    nc match {
      case l: LogLine => l
      case _          => LogLine(Coefficients(nc -> Real.BigOne))
    }
}

/*
Evaluates to 0 if left and right are equal, 1 if left > right, and
-1 if left < right.
 */
private final case class Compare private[Compare] (left: Real, right: Real)
    extends NonConstant

private object Compare {
  def apply(left: Real, right: Real): Real =
    (left, right) match {
      case (Infinity, Infinity)       => Real.zero
      case (Infinity, _)              => Real.one
      case (_, Infinity)              => Constant(-1)
      case (NegInfinity, NegInfinity) => Real.zero
      case (NegInfinity, _)           => Constant(-1)
      case (_, NegInfinity)           => Real.one
      case (Constant(a), Constant(b)) =>
        if (a == b)
          Real.zero
        else if (a > b)
          Real.one
        else
          Constant(-1)
      case _ => new Compare(left, right)
    }
}

private final case class Pow private (base: Real, exponent: NonConstant)
    extends NonConstant

/*
Evaluates to the (index-low)'th element of table.
 */
private final class Lookup(val index: NonConstant,
                           val table: Array[Real],
                           val low: Int)
    extends NonConstant

object Lookup {
  def apply(table: Seq[Real]): Real => Real =
    apply(_, table)

  def apply(index: Real, table: Seq[Real], low: Int = 0): Real =
    index match {
      case Infinity | NegInfinity =>
        throw new ArithmeticException("Cannot lookup an infinite number")
      case Constant(v) =>
        if (v.isWhole)
          table(v.toInt - low)
        else
          throw new ArithmeticException("Cannot lookup a non-integral number")
      case nc: NonConstant =>
        new Lookup(nc, table.toArray, low)
    }
}

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
