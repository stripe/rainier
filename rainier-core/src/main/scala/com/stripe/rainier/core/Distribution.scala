package com.stripe.rainier.core

trait Distribution[T] {
  def generator: Generator[T]
}

object Distribution {
  implicit def gen[T, D <: Distribution[T]] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }

  implicit class Ops[T, D <: Distribution[T]](d: D)(
      implicit lh: Likelihood[D, T]) {
    def fit(value: T): RandomVariable[D] =
      RandomVariable.fit(d, value)
    def fit(seq: Seq[T]): RandomVariable[D] =
      RandomVariable.fit(d, seq)
  }
}
