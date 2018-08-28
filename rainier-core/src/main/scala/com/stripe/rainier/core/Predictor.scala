package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
trait Predictor[X, Y] extends Fittable[(X, Y)] {
  protected type P
  protected def placeholder: Placeholder[X, P]

  protected type Z
  protected def create(p: P): Z
  protected def toDistribution(z: Z): Distribution[Y]

  def predict(x: X): Generator[Y] =
    toDistribution(create(placeholder.wrap(x))).generator

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
  def from[X, Y, A, B](fn: A => B)(implicit ev: B <:< Distribution[Y],
                                   ph: Placeholder[X, A]): Predictor[X, Y] =
    new Predictor[X, Y] {
      type P = A
      type Z = B
      val placeholder = ph
      def toDistribution(z: Z) = ev(z)
      def create(p: P): Z = fn(p)
    }

  def fromInt[Y, Z](fn: Real => Z)(
      implicit ev: Z <:< Distribution[Y]): Predictor[Int, Y] =
    from[Int, Y, Real, Z](fn)

  def fromDouble[Y, Z](fn: Real => Z)(
      implicit ev: Z <:< Distribution[Y]): Predictor[Double, Y] =
    from[Double, Y, Real, Z](fn)

  implicit def likelihood[X, Y, B](implicit lh: Likelihood[B, Y]) =
    new Likelihood[Predictor[X, Y] { type Z = B }, (X, Y)] {
      def target(predictor: Predictor[X, Y] { type Z = B }, value: (X, Y)) =
        lh.target(predictor.create(predictor.placeholder.wrap(value._1)),
                  value._2)
    }
}
