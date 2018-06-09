package com.stripe.rainier.compute

class Evaluator(var cache: Map[Real, Double]) extends Numeric[Real] {

  def toDouble(x: Real): Double = cache.get(x) match {
    case Some(v) => v
    case None => {
      val v = eval(x)
      cache += x -> v
      v
    }
  }

  private def eval(real: Real): Double = real match {
    case Infinity        => 1.0 / 0.0
    case NegInfinity     => -1.0 / 0.0
    case Constant(value) => value.toDouble
    case l: Line =>
      l.ax.map { case (r, d) => toDouble(r) * d.toDouble }.sum + l.b.toDouble
    case l: LogLine =>
      l.ax
        .map { case (r, d) => Math.pow(toDouble(r), d.toDouble) }
        .reduce(_ * _)
    case Unary(original, op) =>
      eval(RealOps.unary(Constant(toDouble(original)), op))
    case If(test, nz, z) =>
      if (toDouble(test) == 0.0)
        toDouble(z)
      else
        toDouble(nz)
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
