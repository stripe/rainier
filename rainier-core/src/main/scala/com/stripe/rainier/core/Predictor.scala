package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
trait Predictor[X, Y] extends Likelihood[(X, Y)] {
  private[core] type Q
  private[core] type R
  type P = (Q, R)
  private[core] def create(q: Q): Distribution.Aux[Y, R]
  private[core] def placeholder: Placeholder[X, Q]

  def wrap(value: (X, Y)) = {
    val q = placeholder.wrap(value._1)
    val z = create(q)
    (q, z.wrap(value._2))
  }

  def logDensity(value: P) =
    create(value._1).logDensity(value._2)

  def predict(x: X): Generator[Y] =
    create(placeholder.wrap(x)).generator

  def predict(seq: Seq[X]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      predict(x).map { y =>
        (x, y)
      }
    })
}

/**
  * Predictor object, for fitting data with covariates
  */
object Predictor {
  def from[X, Y, A, B](fn: A => Distribution.Aux[Y, B])(
      implicit ph: Placeholder[X, A]): Predictor[X, Y] =
    new Predictor[X, Y] {
      type Q = A
      type R = B

      val placeholder = ph
      def create(q: Q) = fn(q)
    }

  def fromInt[Y, B](fn: Real => Distribution.Aux[Y, B]): Predictor[Int, Y] =
    from[Int, Y, Real, B](fn)

  def fromDouble[Y, B](
      fn: Real => Distribution.Aux[Y, B]): Predictor[Double, Y] =
    from[Double, Y, Real, B](fn)
}
