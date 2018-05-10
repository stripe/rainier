package rainier.compute

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
    case b: BinaryReal   => b.op(toDouble(b.left), toDouble(b.right))
    case u: UnaryReal    => u.op(toDouble(u.original))
    case Constant(value) => value
    case If(test, nz, z) =>
      if (toDouble(test) == 0.0)
        toDouble(z)
      else
        toDouble(nz)
    case v: Variable => sys.error(s"No value provided for $v")
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
