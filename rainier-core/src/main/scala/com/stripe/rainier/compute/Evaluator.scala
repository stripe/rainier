package com.stripe.rainier.compute

class Evaluator(var cache: Map[Real, BigDecimal]) extends Numeric[Real] {

  def toDouble(x: Real): Double = memoize(x).toDouble

  private def memoize(real: Real): BigDecimal = cache.get(real) match {
    case Some(v) => v
    case None => {
      val v = eval(real)
      cache += real -> v
      v
    }
  }

  private def eval(real: Real): BigDecimal = real match {
    case l: Line => l.ax.map { case (r, d) => memoize(r) * d }.sum + l.b
    case l: LogLine =>
      l.ax
        .map {
          case (r, d) => Evaluator.pow(memoize(r), d)
        }
        .reduce(_ * _)
    case Unary(original, op) =>
      eval(RealOps.unary(Real(memoize(original)), op))
    case Constant(value) => value
    case If(test, nz, z) =>
      if (memoize(test) == Real.BigZero)
        memoize(z)
      else
        memoize(nz)
    case v: Variable => sys.error(s"No value provided for $v")
  }

  def compare(x: Real, y: Real): Int = toDouble(x).compare(toDouble(y))
  def fromInt(x: Int): Real = Real(x)
  def minus(x: Real, y: Real): Real = x - y
  def negate(x: Real): Real = x * -1
  def plus(x: Real, y: Real): Real = x + y
  def times(x: Real, y: Real): Real = x * y
  def toFloat(x: Real): Float = toDouble(x).toFloat
  def toInt(x: Real): Int = toDouble(x).toInt
  def toLong(x: Real): Long = toDouble(x).toLong
}

object Evaluator {
  def pow(a: BigDecimal, b: BigDecimal): BigDecimal =
    if (b.isValidInt)
      a.pow(b.toInt)
    else
      BigDecimal(Math.pow(a.toDouble, b.toDouble))
}
