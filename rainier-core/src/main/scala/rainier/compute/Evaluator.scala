package rainier.compute

import scala.collection.mutable.HashMap

class Evaluator(variables: Map[Variable, Double]) extends Numeric[Real] {
  private val cache = HashMap.empty[Real, Double]

  def toDouble(x: Real): Double = cache.get(x) match {
    case Some(v) => v
    case None => {
      val v = eval(x)
      cache.update(x, v)
      v
    }
  }

  private def eval(real: Real): Double = real match {
    case l: Line     => l.ax.map { case (r, d) => toDouble(r) * d }.sum + l.b
    case p: Product  => toDouble(p.left) * toDouble(p.right)
    case v: Variable => variables(v)
    case Pow(original, exponent) =>
      Math.pow(toDouble(original), toDouble(exponent))
    case Unary(original, op) =>
      eval(Constant(toDouble(original)).unary(op))
    case Constant(value) => value
    case If(test, nz, z) =>
      if (toDouble(test) == 0.0)
        toDouble(z)
      else
        toDouble(nz)
  }

  def compare(x: Real, y: Real) = toDouble(x).compare(toDouble(y))
  def fromInt(x: Int) = Real(x)
  def minus(x: Real, y: Real) = x - y
  def negate(x: Real) = x * -1
  def plus(x: Real, y: Real) = x + y
  def times(x: Real, y: Real) = x * y
  def toFloat(x: Real) = toDouble(x).toFloat
  def toInt(x: Real) = toDouble(x).toInt
  def toLong(x: Real) = toLong(x).toInt
}
