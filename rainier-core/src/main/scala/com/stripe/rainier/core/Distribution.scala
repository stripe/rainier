package com.stripe.rainier.core

trait Distribution[T] {
  def generator: Generator[T]
  def likelihood: Likelihood[T]

  def fit(value: T): RandomVariable[Distribution[T]] =
    fit(List(value))
  def fit(seq: Seq[T]): RandomVariable[Distribution[T]] =
    likelihood.fit(seq).map { _ =>
      this
    }
}

object Distribution {
  implicit def lh[T, D <: Distribution[T]] =
    new ToLikelihood[D, T] {
      def apply(d: D) = d.likelihood
    }

  def gen[D <: Distribution[T], T] = new ToGenerator[D, T] {
    def apply(d: D) = d.generator
  }
}
