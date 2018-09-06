package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Likelihood[-L, -T] {
  def target(pdf: L, value: T): Target
  def sequence(pdf: L, seq: Seq[T]) =
    new Target(Real.sum(seq.map { t =>
      target(pdf, t).toReal
    }))
}

object Likelihood {
  def from[L, T, U](f: (L, U) => Real)(
      implicit ph: Placeholder[T, U]): Likelihood[L, T] =
    new Likelihood[L, T] {
      def target(likelihood: L, value: T) =
        new Target(f(likelihood, ph.wrap(value)))
    }
}
