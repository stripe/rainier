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
  private[core] def mapping: Mapping[X, Q]

  def wrap(value: (X, Y)) = {
    val q = mapping.wrap(value._1)
    val z = create(q)
    (q, z.wrap(value._2))
  }

  def placeholder() = {
    val ph1 = mapping.placeholder
    val z = create(ph1.value)
    val ph2 = z.placeholder()
    ph1.zip(ph2)
  }

  def logDensity(value: P) =
    create(value._1).logDensity(value._2)

  def predict(x: X): Generator[Y] =
    create(mapping.wrap(x)).generator

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
      implicit m: Mapping[X, A]): Predictor[X, Y] =
    new Predictor[X, Y] {
      type Q = A
      type R = B

      val mapping = m
      def create(q: Q) = fn(q)
    }

  def fromInt[Y, B](fn: Real => Distribution.Aux[Y, B]): Predictor[Int, Y] =
    from[Int, Y, Real, B](fn)

  def fromDouble[Y, B](
      fn: Real => Distribution.Aux[Y, B]): Predictor[Double, Y] =
    from[Double, Y, Real, B](fn)
}
