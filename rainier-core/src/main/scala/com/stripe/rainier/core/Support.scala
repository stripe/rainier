package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * A trait for objects representing the support of a continuous distribution.
  * Specifies a function to transform a real-valued variable to this range,
  * and its log-jacobian.
  */
private[rainier] trait Support {
  def transform(v: Variable): Real

  def logJacobian(v: Variable): Real
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
case class BoundedSupport(min: Real, max: Real) extends Support {
  def transform(v: Variable): Real =
    (Real.one / (Real.one + (v * -1).exp)) * (max - min) + min

  def logJacobian(v: Variable): Real =
    transform(v).log + (1 - transform(v)).log + (max - min).log
}

/**
  * A support representing an open-above {r > k} interval.
  * @param min The lower bound of the distribution
  */
case class BoundedBelowSupport(min: Real = Real.zero) extends Support {
  def transform(v: Variable): Real =
    v.exp + min

  def logJacobian(v: Variable): Real = v
}

/**
  * A support representing an open-below {r < k} interval.
  * @param max The upper bound of the distribution
  */
case class BoundedAboveSupport(max: Real = Real.zero) extends Support {
  def transform(v: Variable): Real =
    max - (-1 * v).exp

  def logJacobian(v: Variable): Real = v
}
