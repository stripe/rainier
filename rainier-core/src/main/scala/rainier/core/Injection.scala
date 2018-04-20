package rainier.core

import rainier.compute._

trait Injection {
  def forwards(x: Real): Real
  def backwards(y: Real): Real
  def isDefinedAt(y: Double): Boolean

  /*
    See https://en.wikipedia.org/wiki/Probability_density_function#Dependent_variables_and_change_of_variables
    This function should be log(d/dy backwards(y)), where y = forwards(x).
    TODO: better comment here
   */
  def logJacobian(x: Real): Real

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

    def param = dist.param.flatMap { x =>
      RandomVariable(forwards(x), logJacobian(x))
    }
  }
}

case class Scale(a: Real) extends Injection {
  def forwards(x: Real) = x * a
  def backwards(y: Real) = y / a
  def isDefinedAt(y: Double) = true
  def logJacobian(x: Real) = Real.zero
}

case class Translate(b: Real) extends Injection {
  def forwards(x: Real) = x + b
  def backwards(y: Real) = y - b
  def isDefinedAt(y: Double) = true
  def logJacobian(x: Real) = Real.zero
}

object Exp extends Injection {
  def forwards(x: Real) = x.log
  def backwards(y: Real) = y.exp
  def isDefinedAt(y: Double) = y >= 0
  def logJacobian(x: Real) = x
}
