package com.stripe.rainier.core

/**
  * Likelihood trait, declaring the `fit` method for conditioning
  */
trait Likelihood[T] {
  def fit(t: T): RandomVariable[Likelihood[T]]
  def fit(seq: Seq[T]): RandomVariable[Likelihood[T]]
}
