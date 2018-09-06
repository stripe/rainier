package com.stripe.rainier.core

/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z, A](implicit ev: Z <:< Distribution[Y],
                                     placeholder: Placeholder[X, A])
    extends Fittable[(X, Y)] {
  def apply(x: X): Z = create(placeholder.wrap(x))
  def create(a: A): Z

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
  class PredictorFactory[X] {
    def apply[Y, Z, A](fn: A => Z)(
        implicit ev: Z <:< Distribution[Y],
        placeholder: Placeholder[X, A]): Predictor[X, Y, Z, A] =
      new Predictor[X, Y, Z, A] {
        def create(a: A): Z = fn(a)
      }
  }

  def from[X] = new PredictorFactory[X]

  implicit def likelihood[X, Y, Z, A](implicit lh: Likelihood[Z, Y]) =
    new Likelihood[Predictor[X, Y, Z, A], (X, Y)] {
      def target(predictor: Predictor[X, Y, Z, A], value: (X, Y)) =
        lh.target(predictor(value._1), value._2)
    }
}
