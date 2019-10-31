package com.stripe.rainier.core

trait Distribution[T] {
  def generator: Generator[T]
}
