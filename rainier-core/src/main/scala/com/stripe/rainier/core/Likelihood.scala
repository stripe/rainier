package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.unused
import scala.language.implicitConversions

/**
  * Likelihood typeclass, declaring the `fit` methods for conditioning
  * (via complex series of implicits.)
  */
trait Likelihood[T]

object Likelihood {
  case class Ops[T, L](likelihood: L)(implicit @unused ev: L <:< Likelihood[T],
                                      fn: Fn[L, T]) {
    def fit(value: T): RandomVariable[L] =
      RandomVariable(likelihood, fn(likelihood, value))
    def fit(seq: Seq[T]): RandomVariable[L] =
      RandomVariable(likelihood, Real.sum(seq.map { t =>
        fn(likelihood, t)
      }))
  }

  implicit def ops[T, L](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                        fn: Fn[L, T]) =
    Ops(likelihood)

  trait Fn[L, T] {
    def apply(likelihood: L, value: T): Real
  }

  object Fn {
    implicit def placeholder[L, T, U](implicit ph: Placeholder[T, U],
                                      f: Fn[L, U]): Fn[L, T] =
      new Fn[L, T] {
        def apply(likelihood: L, value: T) = f(likelihood, ph.wrap(value))
      }
  }

  def fn[L, T](f: (L, T) => Real): Fn[L, T] = new Fn[L, T] {
    def apply(likelihood: L, value: T) = f(likelihood, value)
  }
}
