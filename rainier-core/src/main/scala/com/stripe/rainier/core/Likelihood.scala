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
  case class FitOps[L, T](likelihood: L, tf: Fittable[L, T])(
      implicit ev: L <:< Likelihood[T]) {
    def fit(value: T): RandomVariable[L] =
      new RandomVariable(likelihood, Set(tf.target(likelihood, value)))

    def fit(seq: Seq[T]): RandomVariable[L] =
      new RandomVariable(likelihood, Set(tf.target(likelihood, seq)))

    def toLikelihood: Likelihood[T] = ev(likelihood)
  }

  sealed trait Fittable[L, T] {
    def target(likelihood: L, value: T): Target
    def target(likelihood: L, seq: Seq[T]): Target
    def ops(likelihood: L)(implicit ev: L <:< Likelihood[T]): FitOps[L, T] =
      FitOps(likelihood, this)
  }

  private final case class SimpleFittable[L, T](fn: Fn[L, T])
      extends Fittable[L, T] {
    def target(likelihood: L, value: T) =
      Target(fn(likelihood, value))
    def target(likelihood: L, seq: Seq[T]) =
      Target(Real.sum(seq.map { t =>
        fn(likelihood, t)
      }))
  }

  private final case class PlaceholderFittable[L, T, U](
      fn: Fn[L, U],
      ph: Placeholder[T, U]
  ) extends Fittable[L, T] {
    def target(likelihood: L, value: T) =
      Target(fn(likelihood, ph.wrap(value)))
    def target(likelihood: L, seq: Seq[T]) = {
      val (placeholder, variables) = ph.create()
      val real = fn(likelihood, placeholder)
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
  }

  trait Fn[L, T] {
    def apply(likelihood: L, value: T): Real
  }

  def fn[L, T](f: (L, T) => Real) = new Fn[L, T] {
    def apply(likelihood: L, value: T) = f(likelihood, value)
  }

  implicit def fitOps[L, T](likelihood: L)(implicit ev: L <:< Likelihood[T],
                                           tf: Fittable[L, T]) =
    tf.ops(likelihood)

  implicit def simple[L, T](implicit fn: Fn[L, T]): Fittable[L, T] =
    SimpleFittable(fn)

  implicit def placeholder[L, T, U](implicit fn: Fn[L, U],
                                    ph: Placeholder[T, U]): Fittable[L, T] =
    PlaceholderFittable(fn, ph)
}
