package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

/**
  * Basic probability distribution trait
  */
abstract class Distribution[T, V](implicit placeholder: Placeholder[T, V])
    extends Likelihood[T] {
  def logDensity(v: V): Real
  def valueLogDensity(t: T): Real = logDensity(placeholder.wrap(t))

  def generator: Generator[T]

  def fit(t: T): RandomVariable[Distribution[T, V]] =
    RandomVariable(this, valueLogDensity(t))
  def fit(list: Seq[T]): RandomVariable[Distribution[T, V]] =
    RandomVariable(this, Real.sum(list.map { t =>
      valueLogDensity(t)
    }))
}
