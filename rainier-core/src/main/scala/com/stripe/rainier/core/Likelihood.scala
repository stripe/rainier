package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Likelihood[T] {
  private[core] type P
  private[core] def wrapping: Wrapping[T, P]
  private[core] def logDensity(value: P): Real

  def target(value: T): Target =
    new Target(logDensity(wrapping.wrap(value)))

  def sequence(seq: Seq[T]) =
    new Target(Real.sum(seq.map { t =>
      target(t).toReal
    }))
}

object Likelihood {
  /*
    We define `fit` in the indirect way below so that the return value of, for example,
    `Normal(1).fit(...)` is `RandomVariable[Normal]` instead of
    `RandomVariable[Likelihood[Double]]`. This solves a similar problem to f-bounded
    polymorphism but without introducing an ugly extra type param.
  */
  implicit class Ops[T, L](lh: L)(implicit ev: L <:< Likelihood[T]) {
    def fit(value: T): RandomVariable[L] = RandomVariable.fit(lh, value)
    def fit(seq: Seq[T]): RandomVariable[L] = RandomVariable.fit(lh, seq)
  }
}
