package com.stripe.rainier.core

import com.stripe.rainier.unused

/**
  * Fittable typeclass, declaring the `fit` methods for conditioning
  * (via complex series of implicits.)
  */
trait Fittable[T]

object Fittable {
  case class Ops[T, L](pdf: L)(implicit lh: Likelihood[L, T]) {
    def fit(value: T): RandomVariable[L] = RandomVariable.fit(pdf, value)
    def fit(seq: Seq[T]): RandomVariable[L] = RandomVariable.fit(pdf, seq)
  }

  implicit def ops[T, L](pdf: L)(implicit lh: Likelihood[L, T],
                                 @unused ev: L <:< Fittable[T]) =
    Ops(pdf)
}
