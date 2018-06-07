package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z](implicit ev: Z <:< Distribution[Y])
    extends Likelihood[(X, Y)] {
  def apply(x: X): Z

  def fit(pair: (X, Y)): RandomVariable[Predictor[X, Y, Z]] =
    RandomVariable(this, ev(apply(pair._1)).logDensity(pair._2))

  def fit(seq: Seq[(X, Y)]): RandomVariable[Predictor[X, Y, Z]] =
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
  def from[X, Y, Z](fn: X => Z)(
      implicit ev: Z <:< Distribution[Y]): Predictor[X, Y, Z] =
    new Predictor[X, Y, Z] {
      def apply(x: X): Z = fn(x)
    }
}
