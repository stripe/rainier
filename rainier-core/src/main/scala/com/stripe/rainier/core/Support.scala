package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * A trait for objects representing the support of a continuous distribution.
  * Specifies a function to transform a real-valued variable to this range,
  * and its log-jacobian.
  */
trait Support { self =>
  def transform(v: Variable): Real

  def logJacobian(v: Variable): Real
}

/**
  * A support representing the whole real line.
  */
object RealSupport extends Support {
  def transform(v: Variable): Real = v

  def logJacobian(v: Variable): Real = Real.zero
}

/**
  * A support representing the open (0, 1) interval.
  */
object OpenUnitSupport extends Support {
  def transform(v: Variable): Real =
    Real.one / (Real.one + (v * -1).exp)

  def logJacobian(v: Variable): Real =
    transform(v).log + (1 - transform(v)).log
}

/**
  * A support representing the open {r > 0} interval.
  */
object PositiveSupport extends Support {
  def transform(v: Variable): Real =
    v.exp

  def logJacobian(v: Variable): Real = v
}
