package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  type P
  def placeholder: Placeholder[T, P]
  def logLikelihood(p: P): Real

  def fit(value: T): RandomVariable[Distribution[T]] =
    fit(List(value))
  def fit(seq: Seq[T]): RandomVariable[Distribution[T]] =
    RandomVariable.fit(this, seq)

  def generator: Generator[T]
}

object Distribution {
  implicit def gen[T, D <: Distribution[T]] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }

  implicit def lh[T, D <: Distribution[T]] =
    new Likelihood[D, T] {
      def apply(d: D) = {
        val p = d.placeholder.create()
        val r = d.logLikelihood(p)
        val ex = new Likelihood.Extractor[T] {
          val variables = d.placeholder.variables(p, Nil)
          def extract(t: T) = d.placeholder.extract(t, Nil)
        }
        (r, ex)
      }
    }
}
