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

  def isDefinedAt(x: Real): Real
}

/**
  * A support representing the whole real line.
  */
object RealSupport extends Support {
  def transform(v: Variable): Real = v

  def logJacobian(v: Variable): Real = Real.zero

  def isDefinedAt(x: Real): Real = Real.one
}

/**
  * A support representing the open (0, 1) interval.
  */
object OpenUnitSupport extends Support {
  def transform(v: Variable): Real =
    Real.one / (Real.one + (v * -1).exp)

  def logJacobian(v: Variable): Real =
    transform(v).log + (1 - transform(v)).log

  // (x > 0) <=> ((x + |x|) != 0) && (x != 0) <=> (x + |x|) * x != 0
  def greaterThan0(x: Real): Real = (x + x.abs) * x

  // x is in (0, 1) <=> (x > 0) && (1 - x > 0)
  def in01(x: Real): Real = greaterThan0(x) * greaterThan0(1 - x)

  def isDefinedAt(x: Real): Real = in01(x)
}

/**
  * A support representing the open {r > 0} interval.
  */
object PositiveSupport extends Support {
  def transform(v: Variable): Real =
    v.exp

  /*
  Jacobian time: we need pdf(x) and we have pdf(f(x)) where f(x) = e^x.
  This is pdf(f(x))f'(x) which is pdf(e^x)e^x.
  If we take the logs we get logPDF(e^x) + x.
   */
  def logJacobian(v: Variable): Real =
    v

  // (x > 0) <=> ((x + |x|) != 0) && (x != 0) <=> (x + |x|) * x != 0
  def greaterThan0(x: Real): Real = (x + x.abs) * x

  def isDefinedAt(x: Real): Real = greaterThan0(x)
}
