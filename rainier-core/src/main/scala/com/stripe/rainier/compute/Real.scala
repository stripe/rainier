package com.stripe.rainier
package compute

import com.stripe.rainier.ir

/*
A Real is a DAG which represents a mathematical function
from 0 or more real-valued input parameters to a single real-valued output.

You can create new single-node DAGs either like `Real(2.0)`, for a constant,
or with `Real.variable` to introduce an input parameter. You can create more interesting DAGs
by combining Reals using the standard mathematical operators, eg `Real.variable + Real(2.0)`
is the function f(x) = x+2, or `Real.variable * Real.variable.log` is the function f(x,y) = xlog(y).
Every such operation on Real results in a new Real.
Apart from Variable and a simple ternary If expression, all of the subtypes of Real are private to this package.

You can also automatically derive the gradient of a Real with respect to its variables.
 */
sealed trait Real {
  def +(other: Real): Real = RealOps.add(this, other)
  def *(other: Real): Real = RealOps.multiply(this, other)

  def unary_- : Real = this * (-1)
  def -(other: Real): Real = this + (-other)
  def /(other: Real): Real = RealOps.divide(this, other)

  def min(other: Real): Real = RealOps.min(this, other)
  def max(other: Real): Real = RealOps.max(this, other)

  def pow(exponent: Real): Real = RealOps.pow(this, exponent)

  def exp: Real = RealOps.unary(this, ir.ExpOp)
  def log: Real = RealOps.unary(this, ir.LogOp)

  def sin: Real = RealOps.unary(this, ir.SinOp)
  def cos: Real = RealOps.unary(this, ir.CosOp)
  def tan: Real = RealOps.unary(this, ir.TanOp)

  def asin: Real = RealOps.unary(this, ir.AsinOp)
  def acos: Real = RealOps.unary(this, ir.AcosOp)
  def atan: Real = RealOps.unary(this, ir.AtanOp)

  def sinh: Real = (this.exp - (-this).exp) / 2
  def cosh: Real = (this.exp + (-this).exp) / 2
  def tanh: Real = this.sinh / this.cosh

  //because abs does not have a smooth derivative, try to avoid using it
  def abs: Real = RealOps.unary(this, ir.AbsOp)

  def logit: Real = -((Real.one / this - 1).log)
  def logistic: Real = Real.one / (Real.one + (-this).exp)

  lazy val variables: List[Variable] = RealOps.variables(this).toList
  lazy val gradient: List[Real] = Gradient.derive(variables, this)

  def writeGraph(path: String): Unit = {
    RealViz("output" -> this).write(path)
  }

  def writeIRGraph(path: String, methodSizeLimit: Option[Int] = None): Unit = {
    RealViz
      .ir(List(("output", this)), variables, false, methodSizeLimit)
      .write(path)
  }
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

  def variable(): Variable = new Placeholder()
  def parameter(fn: Variable => Real): Variable = new Parameter(fn)

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
    Lookup(RealOps.compare(left, right), List(lt, eq, gt), -1)

  private[compute] val BigZero = BigDecimal(0.0)
  private[compute] val BigOne = BigDecimal(1.0)
  private[compute] val BigTwo = BigDecimal(2.0)
  private[compute] val BigPi = BigDecimal(math.Pi)
  val zero: Real = Constant(BigZero)
  val one: Real = Constant(BigOne)
  val two: Real = Constant(BigTwo)
  val Pi: Real = Constant(BigPi)
  val infinity: Real = Infinity
  val negInfinity: Real = NegInfinity
}

final private[rainier] case class Constant(value: BigDecimal) extends Real
final private[rainier] object Infinity extends Real
final private[rainier] object NegInfinity extends Real

sealed trait NonConstant extends Real

sealed trait Variable extends NonConstant {
  private[compute] val param = new ir.Parameter
}

final private[rainier] class Placeholder extends Variable
final private[rainier] class Parameter(fn: Variable => Real) extends Variable {
  def density: Real = fn(this)
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

private[compute] object Line {
  def apply(ax: Coefficients, b: BigDecimal): Line = {
    require(!ax.isEmpty)
    new Line(ax, b)
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
private final case class Compare private (left: Real, right: Real)
    extends NonConstant

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
