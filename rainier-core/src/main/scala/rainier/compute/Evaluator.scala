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
    case p: Real_+ => toDouble(p.original)
    case c: Constant   => c.value
    case b: BinaryReal => b.op(toDouble(b.left), toDouble(b.right))
    case u: UnaryReal  => u.op(toDouble(u.original))
    case v: Variable   => variables(v)
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
