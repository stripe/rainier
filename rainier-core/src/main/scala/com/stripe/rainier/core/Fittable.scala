package com.stripe.rainier.core

import com.stripe.rainier.unused

trait Fittable[T]

object Fittable {
  case class Ops[L, T](pdf: L)(implicit lh: Likelihood[L, T]) {
    def fit(value: T): RandomVariable[L] = RandomVariable.fit(pdf, value)(lh)
    def fit(seq: Seq[T]): RandomVariable[L] = RandomVariable.fit(pdf, seq)(lh)
  }

  implicit def ops[L, T](pdf: L)(implicit lh: Likelihood[L, T],
                                 @unused ev: L <:< Fittable[T]) =
    Ops(pdf)
}
