package rainier.compute

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
  def /(other: Real): Real = this * other.pow(-1)

  def pow[N](exponent: N)(implicit num: Numeric[N]): Real =
    RealOps.pow(this, num.toDouble(exponent))

  def exp: Real = RealOps.unary(this, ExpOp)
  def log: Real = RealOps.unary(this, LogOp)

  //because abs does not have a smooth derivative, try to avoid using it
  def abs: Real = RealOps.unary(this, AbsOp)

  def >(other: Real): Real = RealOps.isPositive(this - other)
  def <(other: Real): Real = RealOps.isNegative(this - other)
  def >=(other: Real): Real = Real.one - (this < other)
  def <=(other: Real): Real = Real.one - (this > other)

  lazy val variables: Seq[Variable] = RealOps.variables(this).toList
  def gradient: Seq[Real] = Gradient.derive(variables, this)
}

object Real {
  implicit def apply[N](value: N)(implicit toReal: ToReal[N]): Real =
    toReal(value)
  def seq[A](as: Seq[A])(implicit toReal: ToReal[A]): Seq[Real] =
    as.map(toReal(_))

  def sum(seq: Seq[Real]): Real =
    seq.foldLeft(Real.zero)(_ + _)

  val zero: Real = Real(0.0)
  val one: Real = Real(1.0)
}

private case class Constant(value: Double) extends Real

sealed trait NonConstant extends Real

class Variable extends NonConstant

private case class Unary(original: NonConstant, op: UnaryOp) extends NonConstant

private sealed trait UnaryOp
private case object ExpOp extends UnaryOp
private case object LogOp extends UnaryOp
private case object AbsOp extends UnaryOp

/*
This node type represents any linear transformation from an input vector to an output
scalar as the function `ax + b`, where x is the input vector, a is a constant vector, ax is their dot product,
and b is a constant scalar.

This is used to represent all additions and any multiplications by constants.

Because it is common for ax to have a large number of terms, this is deliberately not a case class,
as equality comparisons would be too expensive. The impact of this is subtle, see [0] at the bottom of this file
for an example.
*/
private class Line private (val ax: Map[NonConstant, Double], val b: Double)
    extends NonConstant

private object Line {
  def apply(ax: Map[NonConstant, Double], b: Double): Line = {
    require(ax.size > 0)
    new Line(ax, b)
  }
}

/*
This node type represents non-linear transformations from an input vector to a scalar,
of the form `x^a * y^b * z^c ...` where x,y,z are the elements of the input vector,
and a,b,c are constant exponents.
*/
private case class LogLine private (ax: Map[NonConstant, Double])
    extends NonConstant

private object LogLine {
  def apply(ax: Map[NonConstant, Double]): LogLine = {
    require(ax.size > 0)
    new LogLine(ax)
  }
}

/*
This node type represents an expression which is equal to `whenZero` when
test is equal to zero, and `whenNotZero` otherwise. Because this expression
does not have a smooth derivative, it is not recommended that you use this
unless absolutely necessary.
*/
case class If private (test: NonConstant, whenNonZero: Real, whenZero: Real)
    extends NonConstant

object If {
  def apply(test: Real, whenNonZero: Real, whenZero: Real): Real =
    test match {
      case Constant(0.0)   => whenZero
      case Constant(v)     => whenNonZero
      case nc: NonConstant => new If(nc, whenNonZero, whenZero)
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