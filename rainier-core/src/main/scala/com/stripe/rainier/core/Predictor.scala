package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
trait Predictor[X, Y] extends Likelihood[(X, Y)] {
  private[core] type Q
  private[core] type R
  type P = (Q, R)
  private[core] def create(q: Q): Distribution.Aux[Y, R]
  private[core] def xq: Wrapping[X, Q]

  lazy val wrapping = new Wrapping[(X, Y), (Q, R)] {
    def wrap(value: (X, Y)) = {
      val q = xq.wrap(value._1)
      val qr = create(q).wrapping
      (q, qr.wrap(value._2))
    }
    def placeholder(seq: Seq[(X, Y)]) = ???
  }

  def logDensity(value: P) =
    create(value._1).logDensity(value._2)

  def predict(x: X): Generator[Y] =
    create(xq.wrap(x)).generator

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
  def from[X, Y, A, B](fn: A => Distribution.Aux[Y, B])(
      implicit xa: Wrapping[X, A]): Predictor[X, Y] =
    new Predictor[X, Y] {
      type Q = A
      type R = B

      val xq = xa
      def create(q: Q) = fn(q)
    }

  def fromInt[Y, B](fn: Real => Distribution.Aux[Y, B]): Predictor[Int, Y] =
    from[Int, Y, Real, B](fn)

  def fromDouble[Y, B](
      fn: Real => Distribution.Aux[Y, B]): Predictor[Double, Y] =
    from[Double, Y, Real, B](fn)
}
