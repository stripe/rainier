package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.language.implicitConversions
import scala.collection.mutable.ArrayBuffer

/**
  * Likelihood typeclass, declaring the `fit` methods for conditioning
  * (via complex series of implicits.)
  */
trait Likelihood[T]

object Likelihood {
  case class Fittable[T, L](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                           fn: Fn[L, T]) {
    def fit(value: T): RandomVariable[L] =
      new RandomVariable(likelihood, Set(fn(likelihood, value)))

    def fit(seq: Seq[T]): RandomVariable[L] =
      new RandomVariable(likelihood, Set(fn.seq(likelihood, seq)))

    def toLikelihood: Likelihood[T] = ev(likelihood)
  }

  implicit def fittable[T, L](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                             fn: Fn[L, T]) =
    Fittable(likelihood)

  trait Fn[-L, -T] {
    def apply(likelihood: L, value: T): Target
    def seq(likelihood: L, seq: Seq[T]): Target
  }

  def fn[L, T](f: (L, T) => Real): Fn[L, T] =
    new Fn[L, T] {
      def apply(likelihood: L, value: T) =
        Target(f(likelihood, value))
      def seq(likelihood: L, seq: Seq[T]) =
        Target(Real.sum(seq.map { value =>
          f(likelihood, value)
        }))
    }

  def placeholder[L, T, U](f: (L, U) => Real)(
      implicit ph: Placeholder[T, U]): Fn[L, T] =
    new Fn[L, T] {
      def apply(likelihood: L, value: T) =
        Target(f(likelihood, ph.wrap(value)))
      def seq(likelihood: L, seq: Seq[T]) = {
        val (placeholder, variables) = ph.create()
        val real = f(likelihood, placeholder)
        val arrayBufs =
          variables.map { v =>
            v -> new ArrayBuffer[Double]
          }.toMap
        seq.foreach { t =>
          val map = ph.mapping(t, placeholder)
          arrayBufs.foreach {
            case (v, a) => a += map(v)
          }
        }
        new Target(real, arrayBufs.map { case (v, a) => v -> a.toArray })
      }
    }
}
