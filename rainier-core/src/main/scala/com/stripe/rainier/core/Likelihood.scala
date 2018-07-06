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
  case class Fittable[L, T](
      likelihood: L,
      fn: T => Target,
      seqFn: Seq[T] => Target)(implicit ev: L <:< Likelihood[T]) {
    def fit(value: T): RandomVariable[L] =
      new RandomVariable(likelihood, Set(fn(value)))

    def fit(seq: Seq[T]): RandomVariable[L] =
      new RandomVariable(likelihood, Set(seqFn(seq)))

    def toLikelihood: Likelihood[T] = ev(likelihood)
  }

  trait ToFittable[-L, T] {
    def apply(likelihood: L)(implicit ev: L <:< Likelihood[T]): Fittable[L, T]
  }

  implicit def fittable[L, T](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                             tf: ToFittable[L, T]) =
    tf(likelihood)

  def fn[L, T](f: (L, T) => Real): ToFittable[L, T] =
    new ToFittable[L, T] {
      def apply(likelihood: L)(implicit ev: L <:< Likelihood[T]) =
        Fittable(
          likelihood, { value: T =>
            Target(f(likelihood, value))
          }, { seq: Seq[T] =>
            Target(Real.sum(seq.map { value =>
              f(likelihood, value)
            }))
          }
        )
    }

  def placeholder[L, T, U](f: (L, U) => Real)(
      implicit ph: Placeholder[T, U]): ToFittable[L, T] =
    new ToFittable[L, T] {
      def apply(likelihood: L)(implicit ev: L <:< Likelihood[T]) =
        Fittable(
          likelihood,
          value => Target(f(likelihood, ph.wrap(value))),
          seq => {
            val (placeholder, variables) = ph.create()
            val real = f(likelihood, placeholder)
            val arrayBufs =
              variables.map { _ =>
                new ArrayBuffer[Double]
              }
            seq.foreach { t =>
              val doubles = ph.extract(t, Nil)
              arrayBufs.zip(doubles).foreach {
                case (a, d) => a += d
              }
            }
            val placeholdersMap =
              variables.zip(arrayBufs.map(_.toArray)).toMap
            new Target(real, placeholdersMap)
          }
        )
    }
}
