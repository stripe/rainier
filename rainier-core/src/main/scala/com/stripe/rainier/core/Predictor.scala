package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, P, Y, Z](implicit ev: Z <:< Distribution[Y, _],
                                     placeholder: Placeholder[X, P])
    extends Likelihood[(X, Y)] {
  def apply(x: P): Z
  def apply(x: X): Z = apply(placeholder.wrap(x))

  def fit(pair: (X, Y)): RandomVariable[Predictor[X, P, Y, Z]] =
    RandomVariable(this, ev(apply(pair._1)).logDensity(pair._2))

  def fit(seq: Seq[(X, Y)]): RandomVariable[Predictor[X, P, Y, Z]] =
    RandomVariable(this, Real.sum(seq.map {
      case (x, y) => ev(apply(x)).logDensity(y)
    }))

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
  def from[X] = new From[X]

  class From[X] {
    def apply[P, Y, Z](fn: P => Z)(
        implicit placeholder: Placeholder[X, P],
        ev: Z <:< Distribution[Y, _]): Predictor[X, P, Y, Z] =
      new Predictor[X, P, Y, Z] {
        def apply(x: P): Z = fn(x)
      }
  }
}
