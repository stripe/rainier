package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

/**
  * Basic probability distribution trait
  */
abstract class Distribution[T,P](implicit placeholder: Placeholder[T,P])
  extends Likelihood[T] {
  def logDensity(t: P): Real

  def generator: Generator[T]

  def fit(t: T): RandomVariable[Distribution[T]] =
    RandomVariable(this, logDensity(placeholder.wrap(t)))
  def fit(list: Seq[T]): RandomVariable[Distribution[T]] =
    RandomVariable(this, Real.sum(list.map{t => logDensity(placeholder.wrap(t))}))
}
