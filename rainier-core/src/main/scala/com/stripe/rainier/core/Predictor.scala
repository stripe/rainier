package com.stripe.rainier.core
/*
Predictor fn: X-placeholder => Z
vs Dispatcher: Map[K,Z], fn: X => K
 */
/**
  * Predictor class, for fitting data with covariates
  */
abstract class Predictor[X, Y, Z, A](implicit ev: Z <:< Distribution[Y],
                                     ph: Placeholder[X, A]) {
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
  def from[X, Y, Z, A](fn: A => Z)(
      implicit ev: Z <:< Distribution[Y],
      ph: Placeholder[X, A]): Predictor[X, Y, Z, A] =
    new Predictor[X, Y, Z, A] {
      def create(a: A): Z = fn(a)
    }

  implicit def likelihood[L, X, Y, Z, A, B](implicit
                                            lh: PlaceholderLikelihood[Z, Y, B],
                                            ph: Placeholder[X, A],
                                            ev: L <:< Predictor[X, Y, Z, A]) = {
    implicit val ph2 = lh.ph
    new PlaceholderLikelihood[L, (X, Y), (A, B)] {
      def logDensity(pdf: L, value: (A, B)) =
        lh.logDensity(ev(pdf).create(value._1), value._2)
    }
  }
}
