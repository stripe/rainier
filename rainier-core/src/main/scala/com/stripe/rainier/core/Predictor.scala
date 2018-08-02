package com.stripe.rainier.core

/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z](implicit ev: Z <:< Distribution[Y])
    extends Fittable[(X, Y)] {
  def apply(x: X): Z

  def predict(x: X): Generator[Y] = ev(apply(x)).generator

  def predict(seq: Seq[X]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      ev(apply(x)).generator.map { y =>
        (x, y)
      }
    })
}

/**
  * Predictor object, for fitting data with covariates
  */
object Predictor {
  def from[X, Y, Z](fn: X => Z)(
      implicit ev: Z <:< Distribution[Y]): Predictor[X, Y, Z] =
    new Predictor[X, Y, Z] {
      def apply(x: X): Z = fn(x)
    }

  implicit def likelihood[X, Y, Z](implicit lh: Likelihood[Z, Y]) =
    new Likelihood[Predictor[X, Y, Z], (X, Y)] {
      def target(predictor: Predictor[X, Y, Z], value: (X, Y)) =
        lh.target(predictor(value._1), value._2)
    }
}
