package com.stripe.rainier.core

/**
  * Basic probability distribution trait
  */
trait Distribution[T] {
  def generator: Generator[T]
}
