package com.stripe.rainier.compute

import com.stripe.rainier.ir

/*
A Real is a DAG which represents a mathematical function
from 0 or more real-valued input parameters to a single real-valued output.
 */
sealed trait Real {
  def bounds: Bounds

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

  def abs: Real = RealOps.unary(this, ir.AbsOp)

  def logit: Real = -((Real.one / this - 1).log)
  def logistic: Real = Real.one / (Real.one + (-this).exp)
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

  def parameter(): Parameter = new Parameter(new Prior(Real.zero))
  def parameter(fn: Parameter => Real): Parameter = {
    val x = parameter()
    x.prior = new Prior(fn(x))
    x
  }

  def parameters(size: Int)(
      fn: Vector[Parameter] => Real): Vector[Parameter] = {
    val vector = Vector.fill(size)(parameter())
    val prior = new Prior(fn(vector))
    vector.toList.foreach { x =>
      x.prior = prior
    }
    vector
  }

  def doubles(seq: Seq[Double]): Real = new Column(seq.toArray)
  def longs(seq: Seq[Long]): Real = doubles(seq.map(_.toDouble))

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

  val zero: Real = Constant.Zero
  val one: Real = Constant.One
  val two: Real = Constant.Two
  val negOne: Real = Constant.NegOne
  val Pi: Real = Constant.Pi
  val infinity: Real = Constant.Infinity
  val negInfinity: Real = Constant.NegInfinity
}

sealed trait Constant extends Real {
  def isZero: Boolean =
    bounds.lower == 0.0 && bounds.upper == 0.0
  def isOne: Boolean =
    bounds.lower == 1.0 && bounds.upper == 1.0
  def isTwo: Boolean =
    bounds.lower == 2.0 && bounds.upper == 2.0
  def isPosInfinity: Boolean =
    bounds.lower.isPosInfinity && bounds.upper.isPosInfinity
  def isNegInfinity: Boolean =
    bounds.lower.isNegInfinity && bounds.upper.isNegInfinity
  def isPositive: Boolean =
    bounds.lower >= 0.0

  def getDouble: Double
  def map(fn: Double => Double): Constant
  def mapWith(other: Constant)(fn: (Double, Double) => Double): Constant
  def +(other: Constant): Constant = ConstantOps.add(this, other)
  def *(other: Constant): Constant = ConstantOps.multiply(this, other)
  def /(other: Constant): Constant = ConstantOps.divide(this, other)
}

object Constant {
  val Zero: Constant = Scalar(0.0)
  val One: Constant = Scalar(1.0)
  val Two: Constant = Scalar(2.0)
  val NegOne: Constant = Scalar(-1.0)
  val NegTwo: Constant = Scalar(-2.0)
  val Pi: Constant = Scalar(math.Pi)
  val Infinity: Constant = Scalar(Double.PositiveInfinity)
  val NegInfinity: Constant = Scalar(Double.NegativeInfinity)
}

final private case class Scalar(value: Double) extends Constant {
  val bounds = Bounds(value, value)
  def getDouble = value
  def map(fn: Double => Double) = Scalar(fn(value))
  def mapWith(other: Constant)(fn: (Double, Double) => Double) =
    other match {
      case Scalar(v) => Scalar(fn(value, v))
      case c: Column =>
        c.map { v =>
          fn(value, v)
        }
    }
}

final private[rainier] class Column(val values: Array[Double])
    extends Constant {
  val param = new ir.Param
  val bounds = Bounds(values.min, values.max)
  def getDouble = sys.error("Not a scalar")
  def map(fn: Double => Double) = new Column(values.map(fn))
  def mapWith(other: Constant)(fn: (Double, Double) => Double) =
    other match {
      case Scalar(v) =>
        map { u =>
          fn(u, v)
        }
      case c: Column =>
        new Column(values.zip(c.values).map { case (u, v) => fn(u, v) })
    }

  def maybeScalar: Option[Double] =
    if (bounds.lower == bounds.upper)
      Some(bounds.lower)
    else
      None
}

sealed trait NonConstant extends Real

final private[rainier] class Parameter(var prior: Prior) extends NonConstant {
  val param = new ir.Param
  val bounds = Bounds(Double.NegativeInfinity, Double.PositiveInfinity)
}

private[rainier] class Prior(val density: Real)

final private case class Unary(original: NonConstant, op: ir.UnaryOp)
    extends NonConstant {
  val bounds = op match {
    case ir.NoOp  => original.bounds
    case ir.AbsOp => Bounds.abs(original.bounds)
    case ir.ExpOp => Bounds.exp(original.bounds)
    case ir.LogOp => Bounds.log(original.bounds)
    //todo: narrow bounds for trig
    case ir.SinOp | ir.CosOp               => Bounds(-1, 1)
    case ir.TanOp                          => Bounds(Double.NegativeInfinity, Double.PositiveInfinity)
    case ir.AsinOp | ir.AcosOp | ir.AtanOp => Bounds(0, Math.PI / 2.0)
  }
}

/*
This node type represents any linear transformation from an input vector to an output
scalar as the function `ax + b`, where x is the input vector, a is a constant vector, ax is their dot product,
and b is a constant scalar.

This is used to represent all additions and any multiplications by constants.

Because it is common for ax to have a large number of terms, this is deliberately not a case class,
as equality comparisons would be too expensive. The impact of this is subtle, see [0] at the bottom of this file
for an example.
 */
private final class Line private (val ax: Coefficients, val b: Constant)
    extends NonConstant {
  val bounds = Bounds.sum(b.bounds :: ax.toList.map {
    case (x, a) =>
      Bounds.multiply(x.bounds, a.bounds)
  })
}

private[compute] object Line {
  def apply(ax: Coefficients, b: Constant): Line = {
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
  val bounds =
    ax.toList
      .map { case (x, a) => Bounds.pow(x.bounds, a.bounds) }
      .reduce { (l, r) =>
        Bounds.multiply(l, r)
      }
}

private object LogLine {
  def apply(nc: NonConstant): LogLine =
    nc match {
      case l: LogLine => l
      case _          => LogLine(Coefficients(nc))
    }
}

/*
Evaluates to 0 if left and right are equal, 1 if left > right, and
-1 if left < right.
 */
private final case class Compare private (left: Real, right: Real)
    extends NonConstant {
  val bounds = Bounds(-1, 1)
}

private final case class Pow private (base: Real, exponent: NonConstant)
    extends NonConstant {
  val bounds = Bounds.pow(base.bounds, exponent.bounds)
}

/*
Evaluates to the (index-low)'th element of table.
 */
private final class Lookup(val index: Real,
                           val table: Array[Real],
                           val low: Int)
    extends NonConstant {
  val bounds = Bounds.or(table.map(_.bounds))
}

object Lookup {
  def apply(table: Seq[Real]): Real => Real =
    apply(_, table)

  def apply(index: Real, table: Seq[Real], low: Int = 0): Real =
    index match {
      case Scalar(v) =>
        lookup(v, table, low)
      case c: Column =>
        c.maybeScalar match {
          case Some(v) => lookup(v, table, low)
          case None =>
            val scalars = table.collect { case Scalar(v) => v }.toVector
            if (scalars.size == table.size)
              c.map { d =>
                if (d.isWhole)
                  scalars(d.toInt - low)
                else
                  throw new ArithmeticException(
                    "Cannot lookup a non-integral number")
              } else
              new Lookup(index, table.toArray, low)
        }
      case _ =>
        new Lookup(index, table.toArray, low)
    }

  private def lookup(index: Double, table: Seq[Real], low: Int): Real =
    if (index.isWhole)
      table(index.toInt - low)
    else
      throw new ArithmeticException("Cannot lookup a non-integral number")
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
