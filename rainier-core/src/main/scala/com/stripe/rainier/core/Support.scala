package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * A trait for objects representing the support of a continuous distribution.
  * Specifies a function to transform a real-valued variable to this range,
  * and its log-jacobian.
  */
trait Support {
  def transform(v: Variable): Real

  def logJacobian(v: Variable): Real
}

object Support {
  def apply(min: Real = Real.negInfinity, max: Real = Real.infinity): Support =
    (min, max) match {
      case (Real.negInfinity, Real.infinity) => UnboundedSupport
      case (min, Real.infinity)              => BoundedBelowSupport(min)
      case (Real.negInfinity, max)           => BoundedAboveSupport(max)
      case (min, max)                        => BoundedSupport(min, max)
    }
}

/**
  * A support representing the whole real line.
  */
object UnboundedSupport extends Support {
  def transform(v: Variable): Real = v

  def logJacobian(v: Variable): Real = Real.zero
}

/**
  * A support representing a bounded (min, max) interval.
  */
case class BoundedSupport(a: Real, b: Real) extends Support {
  val min = If(a < b, a, b)
  val max = If(a < b, b, a)

  def transform(v: Variable): Real =
    (Real.one / (Real.one + (v * -1).exp)) * (max - min) + min

  def logJacobian(v: Variable): Real =
    transform(v).log + (1 - transform(v)).log + (max - min).log
}

/**
  * A support representing an open-above {r > k} or open-below {r < k} interval.
  * @param bound The bound (above or below) of the distribution
  * @param arity The arity of the distribution: positive for a distribution unbounded above, negative for bounded below.
  */
case class HalfBoundedSupport(bound: Real = Real.zero, arity: Real = Real.one) extends Support {
  val max = Real.infinity

  def transform(v: Variable): Real =
    v.exp * arity + bound

  def logJacobian(v: Variable): Real = v * arity
}