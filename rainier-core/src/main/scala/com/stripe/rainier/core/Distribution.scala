package com.stripe.rainier.core

trait Distribution[T] {
  def generator: Generator[T]
}

object Distribution {
  def gen[D <: Distribution[T], T]: ToGenerator[D, T] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }
}
