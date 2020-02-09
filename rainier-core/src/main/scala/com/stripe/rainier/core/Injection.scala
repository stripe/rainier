package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.unused

/**
  * Injective transformations
  */
private[rainier] trait Injection { self =>
  def forwards(x: Real): Real
  def fastForwards(x: Double, n: Evaluator): Double =
    n.toDouble(forwards(Real(x)))
  def backwards(y: Real): Real
  def whenDefinedAt(@unused y: Real,
                    ifDefined: Real,
                    @unused notDefined: Real): Real = ifDefined
  def requirements: Set[Real]
  def transformSupport(supp: Support): Support
  /*
    See https://en.wikipedia.org/wiki/Probability_density_function#Dependent_variables_and_change_of_variables
    This function should be log(d/dy backwards(y)), where y = forwards(x).
   */
  def logJacobian(y: Real): Real

  def transform(dist: Continuous): Continuous = new Continuous {
    val support: Support = transformSupport(dist.support)

    def logDensity(real: Real): Real =
      whenDefinedAt(real,
                    dist.logDensity(backwards(real)) + logJacobian(real),
                    Real.negInfinity)

    val generator: Generator[Double] = {
      val distGen = dist.generator
      Generator.require(self.requirements ++ distGen.requirements) { (r, n) =>
        fastForwards(distGen.get(r, n), n)
      }
    }

    def latent: Real = forwards(dist.latent)
  }
}

/**
  * Class to scale a distribution under multiplication by a positive scale factor.
  * We assume that (a > 0).
  */
final case class Scale(a: Real) extends Injection {
  private val lj = a.log * -1
  def forwards(x: Real): Real = x * a
  override def fastForwards(x: Double, n: Evaluator) =
    x * n.toDouble(a)
  def backwards(y: Real): Real = y / a
  def logJacobian(y: Real): Real = lj
  val requirements: Set[Real] = Set(a)

  def transformSupport(supp: Support): Support = supp match {
    case UnboundedSupport         => UnboundedSupport
    case BoundedBelowSupport(min) => BoundedBelowSupport(forwards(min))
    case BoundedAboveSupport(max) => BoundedAboveSupport(forwards(max))
    case BoundedSupport(min, max) =>
      BoundedSupport(forwards(min), forwards(max))
  }
}

/**
  * Class to translate a distribution by adding a constant.
  */
final case class Translate(b: Real) extends Injection {
  def forwards(x: Real): Real = x + b
  override def fastForwards(x: Double, n: Evaluator) =
    x + n.toDouble(b)
  def backwards(y: Real): Real = y - b
  def logJacobian(y: Real): Real = Real.zero
  val requirements: Set[Real] = Set(b)

  def transformSupport(supp: Support): Support = supp match {
    case UnboundedSupport         => UnboundedSupport
    case BoundedBelowSupport(min) => BoundedBelowSupport(forwards(min))
    case BoundedAboveSupport(max) => BoundedAboveSupport(forwards(max))
    case BoundedSupport(a, b)     => BoundedSupport(forwards(a), forwards(b))
  }
}

/**
  * Object to exponentiate a distribution.
  */
object Exp extends Injection {
  def forwards(x: Real): Real = x.exp
  override def fastForwards(x: Double, n: Evaluator) =
    Math.exp(x)
  def backwards(y: Real): Real = y.log
  def logJacobian(y: Real): Real = y.log * -1

  override def whenDefinedAt(y: Real,
                             whenDefined: Real,
                             notDefined: Real): Real =
    Real.gt(y, Real.zero, whenDefined, notDefined)
  val requirements: Set[Real] = Set.empty

  def transformSupport(supp: Support): Support = supp match {
    case UnboundedSupport         => UnboundedSupport
    case BoundedBelowSupport(min) => BoundedBelowSupport(forwards(min))
    case BoundedAboveSupport(max) => BoundedSupport(Real.zero, forwards(max))
    case BoundedSupport(a, b)     => BoundedSupport(forwards(a), forwards(b))
  }
}
