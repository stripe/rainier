package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
sealed trait Predictor[L, X] { self =>
  private[core] type P

  private[core] def create(p: P): L
  private[core] def xp: Wrapping[X, P]

  def fit[Y](values: Seq[(X, Y)])(
      implicit lh: Likelihood[Predictor[L, X] { type P = self.P }, (X, Y)])
    : RandomVariable[Predictor[L, X]] =
    RandomVariable.fit(this: Predictor[L, X] { type P = self.P }, values)(lh)

  def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]): Generator[Y] =
    gen(create(xp.wrap(x)))

  def predict[Y](seq: Seq[X])(
      implicit gen: ToGenerator[L, Y]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      predict(x).map { y =>
        (x, y)
      }
    })
}

object Predictor {
  implicit def likelihood[X, Y, Q, L](implicit lh: Likelihood[L, Y])
    : Likelihood[Predictor[L, X] { type P = Q }, (X, Y)] =
    new Likelihood[Predictor[L, X] { type P = Q }, (X, Y)] {
      type P = (Q, lh.P)
      def wrapping(l: Predictor[L, X] { type P = Q }) =
        new Wrapping[(X, Y), P] {
          def wrap(value: (X, Y)) = {
            val q = l.xp.wrap(value._1)
            val qr = lh.wrapping(l.create(q))
            (q, qr.wrap(value._2))
          }
          def placeholder(seq: Seq[(X, Y)]) = {
            val qph = l.xp.placeholder(seq.map(_._1))
            val z = l.create(qph.value)
            qph.zip(lh.wrapping(z).placeholder(seq.map(_._2)))
          }
        }

      def logDensity(l: Predictor[L, X] { type P = Q }, value: P) =
        lh.logDensity(l.create(value._1), value._2)
    }

  class Maker[X, A](xa: Wrapping[X, A]) {
    def apply[B](fn: A => B): Predictor[B, X] { type P = A } =
      new Predictor[B, X] {
        type P = A

        val xp = xa
        def create(p: P) = fn(p)
      }
  }

  def from[X, A](implicit xa: Wrapping[X, A]) = new Maker(xa)
  def fromInt = from[Int, Real]
  def fromIntPair = from[(Int, Int), (Real, Real)]
  def fromDouble = from[Double, Real]
  def fromDoubleVector = from[Seq[Double], Seq[Real]]
}
