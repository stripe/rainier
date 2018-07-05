package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.language.implicitConversions

/**
  * Likelihood typeclass, declaring the `fit` methods for conditioning
  * (via complex series of implicits.)
  */
trait Likelihood[T]

object Likelihood {
  case class Fittable[T, L](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                           fn: Fn[L, T]) {
    def fit(value: T): RandomVariable[L] =
      RandomVariable(likelihood, fn(likelihood, value))
    def fit(seq: Seq[T]): RandomVariable[L] =
      RandomVariable(likelihood, Real.sum(seq.map { t =>
        fn(likelihood, t)
      }))
    def toLikelihood: Likelihood[T] = ev(likelihood)
  }

  implicit def fittable[T, L](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                             fn: Fn[L, T]) =
    Fittable(likelihood)

  trait Fn[-L, -T] {
    def apply(likelihood: L, value: T): Real
  }

  def fn[L, T](f: (L, T) => Real): Fn[L, T] =
    new Fn[L, T] {
      def apply(likelihood: L, value: T) = f(likelihood, value)
    }

  def placeholder[L, T, U](f: (L, U) => Real)(
      implicit ph: Placeholder[T, U]): Fn[L, T] =
    new Fn[L, T] {
      def apply(likelihood: L, value: T) = f(likelihood, ph.wrap(value))
    }
}
