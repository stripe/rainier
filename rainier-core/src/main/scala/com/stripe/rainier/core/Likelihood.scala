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
  case class Ops[T, L](lh: L)(implicit ev: L <:< Likelihood[T]) {
    def fit(value: T): RandomVariable[L] = RandomVariable.fit(lh, value)
    def fit(seq: Seq[T]): RandomVariable[L] = RandomVariable.fit(lh, seq)
  }

  implicit def ops[T, L](lh: L)(implicit ev: L <:< Likelihood[T]) =
    Ops(lh)
}
