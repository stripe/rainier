package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * Predictor class, for fitting data with covariates
  */
sealed trait Predictor[L, X] { self =>
  type P
  protected def xp: Placeholder[X, P]
  protected def create(p: P): L

  def fit[Y](values: Seq[(X, Y)])(
    implicit lh: Likelihood[L, Y]): RandomVariable[Predictor[L, X]] =
    RandomVariable.fit(this, values)

  def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]): Generator[Y] =
    ??? //gen(create(wrap(x))

  def predict[Y](seq: Seq[X])(
      implicit gen: ToGenerator[L, Y]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      predict(x).map { y =>
        (x, y)
      }
    })
}

object Predictor {
  implicit def likelihood[L, X, Y](
      implicit lh: Likelihood[L, Y]): Likelihood[Predictor[L, X], (X, Y)] =
    new Likelihood[Predictor[L, X], (X, Y)] {
      def apply(l: Predictor[L, X]) = {
        val u = l.xp.create()
        val z = l.create(u)
        val (r, ex) = lh(z)
        val newEx = new Likelihood.Extractor[(X, Y)] {
          val variables = l.xp.variables(u, ex.variables)
          def extract(t: (X, Y)) =
            l.xp.extract(t._1, ex.extract(t._2))
        }
        (r, newEx)
      }
    }

  class Maker[X, A](xa: Placeholder[X, A]) {
    def apply[B](fn: A => B): Predictor[B, X] =
      new Predictor[B, X] {
        type P = A
        val xp = xa
        def create(p: P) = fn(p)
      }
  }

  def from[X, A](implicit xa: Placeholder[X, A]) = new Maker(xa)
  def fromInt = from[Int, Variable]
  def fromIntPair = from[(Int, Int), (Variable, Variable)]
  def fromDouble = from[Double, Variable]
  def fromDoubleVector(size: Int) =
    from[Seq[Double], Seq[Variable]](Placeholder.vector(size))
}
