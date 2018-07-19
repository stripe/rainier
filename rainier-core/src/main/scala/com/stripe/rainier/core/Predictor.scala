package com.stripe.rainier.core

/*
Predictor fn: X-placeholder => Z
vs Dispatcher: Map[K,Z], fn: X => K
 */
/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z, A](implicit ev: Z <:< Distribution[Y],
                                     val mapping: Mapping[X, A])
    extends Fittable[(X, Y)] {
  def apply(x: X): Z = create(mapping.wrap(x))
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
                                   m: Mapping[X, A]) =
      new Predictor[X, Y, Z, A] {
        def create(a: A): Z = fn(a)
      }
  }

  def from[X] = new PredictorFactory[X]

  implicit def likelihood[X, Y, Z, A](implicit lh: Likelihood[Z, Y]) = {
    new Likelihood[Predictor[X, Y, Z, A], (X, Y)] {
      type V = (A, lh.V)
      def wrap(pdf: Predictor[X, Y, Z, A], value: (X, Y)) = {
        val a = pdf.mapping.wrap(value._1)
        val z = pdf.create(a)
        (a, lh.wrap(z, value._2))
      }
      def placeholder(pdf: Predictor[X, Y, Z, A]) = {
        val ph1 = pdf.mapping.placeholder
        val z = pdf.create(ph1.value)
        val ph2 = lh.placeholder(z)
        ph1.zip(ph2)
      }
      def logDensity(pdf: Predictor[X, Y, Z, A], value: V) =
        lh.logDensity(pdf.create(value._1), value._2)
    }
  }
}
