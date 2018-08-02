package com.stripe.rainier.core

/**
  * Basic probability distribution trait
  */
trait Distribution[T] extends Fittable[T] {
  def generator: Generator[T]
}
