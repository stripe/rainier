package com.stripe.rainier.core

/**
  * Basic probability distribution trait
  */
trait Distribution[T] extends Likelihood[T] {
  def generator: Generator[T]
}
