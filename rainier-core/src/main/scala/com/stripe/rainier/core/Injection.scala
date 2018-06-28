package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.unused

/**
  * Injective transformations
  */
trait Injection { self =>

  def forwards(x: Real): Real
  def backwards(y: Real): Real
  def isDefinedAt(@unused y: Real): Real = Real.one
  def requirements: Set[Real]

  /*
    See https://en.wikipedia.org/wiki/Probability_density_function#Dependent_variables_and_change_of_variables
    This function should be log(d/dy backwards(y)), where y = forwards(x).
   */
  def logBackwardsJacobian(y: Real): Real
  def logForwardsJacobian(x: Real): Real

  def transform(dist: Continuous): Continuous = {
    val oldSupport = dist.support

    val newSupport = new Support {
      def transform(v: Variable): Real = forwards(oldSupport.transform(v))

      def logJacobian(v: Variable): Real =
        oldSupport.logJacobian(v) + logForwardsJacobian(oldSupport.transform(v))

      def isDefinedAt(y: Real): Real = oldSupport.isDefinedAt(backwards(y))
    }

    new Continuous {
      val support: Support = newSupport

      def realLogDensity(real: Real): Real =
        /*If(support.isDefinedAt(real),
           dist.realLogDensity(backwards(real)) +
             logBackwardsJacobian(real),
           Real.zero.log)*/
        dist.realLogDensity(backwards(real)) +
          logBackwardsJacobian(real)

      val generator: Generator[Double] =
        Generator.require(self.requirements) { (r, n) =>
          n.toDouble(forwards(dist.generator.get(r, n)))
        }

    }
  }
}

/**
  * Class to scale a distribution under multiplication by a scale factor
  */
final case class Scale(a: Real) extends Injection {
  private val lj = a.log * -1
  def forwards(x: Real): Real = x * a
  def backwards(y: Real): Real = y / a

  def logBackwardsJacobian(y: Real): Real = lj
  def logForwardsJacobian(x: Real): Real = a.log

  val requirements: Set[Real] = Set(a)
}

/**
  * Class to translate a distribution by adding a constant
  */
final case class Translate(b: Real) extends Injection {
  def forwards(x: Real): Real = x + b
  def backwards(y: Real): Real = y - b

  def logBackwardsJacobian(y: Real): Real = Real.zero
  override def logForwardsJacobian(x: Real): Real = Real.zero

  val requirements: Set[Real] = Set(b)
}

/**
  * Object to exponentiate a distribution
  */
object Exp extends Injection {
  def forwards(x: Real): Real = x.exp
  def backwards(y: Real): Real = y.log

  //this is rarely important because it depends solely on y, which is usually data and not parameters
  def logBackwardsJacobian(y: Real): Real = y.log * -1
  override def logForwardsJacobian(x: Real): Real = x

  override def isDefinedAt(y: Real): Real =
    y > 0
  val requirements: Set[Real] = Set.empty
}
