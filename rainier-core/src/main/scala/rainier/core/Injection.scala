package rainier.core

import rainier.compute._

trait Injection {
  def forwards(x: Real): Real
  def backwards(y: Real): Real
  def isDefinedAt(y: Double): Boolean

  def transform(dist: Continuous): Continuous = new Continuous {
    override def logDensity(t: Double) =
      if (isDefinedAt(t))
        realLogDensity(Constant(t))
      else
        Real.zero

    def realLogDensity(real: Real) =
      dist.realLogDensity(backwards(real))

    val generator = Generator.from { (r, n) =>
      n.toDouble(forwards(dist.generator.get(r, n)))
    }

    def param = dist.param.map(forwards)
  }
}

case class Scale(a: Real) extends Injection {
  def forwards(x: Real) = x * a
  def backwards(y: Real) = y / a
  def isDefinedAt(y: Double) = true
}

case class Translate(b: Real) extends Injection {
  def forwards(x: Real) = x + b
  def backwards(y: Real) = y - b
  def isDefinedAt(y: Double) = true
}

object Exp extends Injection {
  def forwards(x: Real) = x.exp
  def backwards(y: Real) = y.log
  def isDefinedAt(y: Double) = y >= 0
}
