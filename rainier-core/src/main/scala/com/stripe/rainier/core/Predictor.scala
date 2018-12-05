package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
sealed trait Predictor[X, L] { self =>
  type P
  protected def encoder: Encoder[X, P]
  protected def create(p: P): L

  def fit[Y](values: Seq[(X, Y)])(
      implicit lh: ToLikelihood[L, Y]): RandomVariable[Predictor[X, L]] =
    Predictor
      .likelihood[L, X, Y](this)
      .fit(values)
      .map { _ =>
        this
      }

  def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]): Generator[Y] =
    gen(create(encoder.wrap(x)))

  def predict[Y](seq: Seq[X])(
      implicit gen: ToGenerator[L, Y]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      predict(x).map { y =>
        (x, y)
      }
    })
}

object Predictor extends LikelihoodMaker {
  def likelihood[L, X, Y](pred: Predictor[X, L])(
      implicit lh: ToLikelihood[L, Y]): Likelihood[(X, Y)] = {
    val (p, vs) = pred.encoder.create(Nil)
    val l = pred.create(p)
    val inner = lh(l)
    new Likelihood[(X, Y)] {
      val real = inner.real
      val placeholders = vs ++ inner.placeholders
      def extract(t: (X, Y)) =
        pred.encoder.extract(t._1, Nil) ++ inner.extract(t._2)
    }
  }

  class Maker[X, A](enc: Encoder[X, A]) {
    def apply[B](fn: A => B): Predictor[X, B] =
      new Predictor[X, B] {
        type P = A
        val encoder = enc
        def create(p: P) = fn(p)
      }
  }

  type M[X, A] = Maker[X, A]
  def maker[X, A](implicit enc: Encoder[X, A]) = new Maker(enc)

  class Fitter[X, A, Y](seq: Seq[(X, Y)], enc: Encoder[X, A]) {
    val m = maker(enc)
    def to[B](fn: A => B)(
        implicit lh: ToLikelihood[B, Y]): RandomVariable[Predictor[X, B]] =
      m(fn).fit(seq)
  }

  def fit[X, Y, A, B](seq: Seq[(X, Y)])(implicit enc: Encoder[X, A]) =
    new Fitter(seq, enc)
}
