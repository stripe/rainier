package com.stripe.rainier.compute

class Evaluator(var cache: Map[Real, Double], placeholderIndex: Option[Int] = None) extends Numeric[Real] {

  def toDouble(x: Real): Double = x match {
    case Constant(v) => v.toDouble
    case Infinity    => Double.PositiveInfinity
    case NegInfinity => Double.NegativeInfinity
    case _ =>
      cache.get(x) match {
        case Some(v) => v
        case None => {
          val v = eval(x)
          cache += x -> v
          v
        }
      }
  }

  private def eval(real: Real): Double = real match {
    case Infinity        => 1.0 / 0.0
    case NegInfinity     => -1.0 / 0.0
    case Constant(value) => value.toDouble
    case l: Line =>
      l.ax.toList.map { case (r, d) => toDouble(r) * d.toDouble }.sum + l.b.toDouble
    case l: LogLine =>
      l.ax.toList.map { case (r, d) => Math.pow(toDouble(r), d.toDouble) }.product
    case Unary(original, op) =>
      // must use Real(_) constructor since Constant(_) constructor would result in undesirable errors
      // at infinities and unhelpful NumberFormatException on NaN, all due to conversion to BigDecimal
      val ev = Real(toDouble(original))
      eval(RealOps.unary(ev, op))
    case Compare(left, right) =>
      eval(RealOps.compare(toDouble(left), toDouble(right)))
    case Pow(base, exponent) =>
      // note: result can be NaN when base < 0 && exponent is negative and not an int
      Math.pow(toDouble(base), toDouble(exponent))
    case l: Lookup =>
      toDouble(l.table(toDouble(l.index).toInt - l.low))
    case p: Parameter => sys.error(s"No value provided for $p")
    case p: Placeholder =>
      placeholderIndex.map{i => p.values(i)}.getOrElse(sys.error(s"Cannot evaluate a placeholder"))
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
