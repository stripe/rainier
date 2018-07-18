package com.stripe.rainier.core

/*
Predictor fn: X-placeholder => Z
vs Dispatcher: Map[K,Z], fn: X => K
 */
/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z, A](implicit ev: Z <:< Distribution[Y],
                                     val ph: Placeholder[X, A]) {
  def apply(x: X): Z = create(ph.wrap(x))
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
    def apply[Y, Z, A](fn: A => Z)(implicit ev: Z <:< Distribution[Y],
                                   ph: Placeholder[X, A]) =
      new Predictor[X, Y, Z, A] {
        def create(a: A): Z = fn(a)
      }
  }

  def from[X] = new PredictorFactory[X]

  implicit def likelihood[L, X, Y, Z, A](implicit
                                         lh: Likelihood[Z, Y],
                                         ev: L <:< Predictor[X, Y, Z, A]) = {
    new Likelihood[L, (X, Y)] {
      type V = (A, lh.V)
      def placeholder(pdf: L) = {
        val predictor = ev(pdf)
        val a = predictor.ph.placeholder
        val z = predictor.create(a)
        val ph2 = lh.placeholder(z)
        Placeholder.zip(predictor.ph, ph2)
      }
      def logDensity(pdf: L, value: V) =
        lh.logDensity(ev(pdf).create(value._1), value._2)
    }
  }
}
