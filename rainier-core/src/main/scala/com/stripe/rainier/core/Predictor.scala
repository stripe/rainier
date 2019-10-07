package com.stripe.rainier.core

import com.stripe.rainier.compute._

sealed trait Predictor[X, L] {
  def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]): Generator[Y]

  def predict[Y](seq: Seq[X])(
      implicit gen: ToGenerator[L, Y]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      predict(x).map { y =>
        (x, y)
      }
    })
}

/**
  * Predictor class, for fitting data with covariates
  */
trait EncoderPredictor[X, L] extends Predictor[X, L] {
  type P
  protected def encoder: Encoder[X] { type U = P }
  protected def create(p: P): L

  def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]): Generator[Y] =
    gen(create(encoder.wrap(x)))

  def fit[Y](values: Seq[(X, Y)])(
      implicit lh: ToLikelihood[L, Y]): RandomVariable[Predictor[X, L]] =
    EncoderPredictor
      .likelihood(this, lh)
      .fit(values)
      .map { _ =>
        this
      }
}

object EncoderPredictor {
  def likelihood[X, L, Y](pred: EncoderPredictor[X, L],
                          lh: ToLikelihood[L, Y]): Likelihood[(X, Y)] = {
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

  implicit def toLikelihood[X, L, Y](implicit lh: ToLikelihood[L, Y])
    : ToLikelihood[EncoderPredictor[X, L], (X, Y)] =
    new ToLikelihood[EncoderPredictor[X, L], (X, Y)] {
      def apply(pred: EncoderPredictor[X, L]) = likelihood(pred, lh)
    }
}

object Predictor {
  def fit[L, X, Z](values: Seq[(X, Z)])(fn: X => L)(
      implicit lh: ToLikelihood[L, Z]): RandomVariable[Predictor[X, L]] = {
    val rvs = values.map { case (x, z) => lh(fn(x)).fit(z) }
    RandomVariable.traverse(rvs).map { _ =>
      new Predictor[X, L] {
        def predict[Y](x: X)(implicit gen: ToGenerator[L, Y]) =
          gen(fn(x))
      }
    }
  }

  def lookup[K, L](map: Map[K, Real])(fn: Real => L): EncoderPredictor[K, L] =
    new EncoderPredictor[K, L] {
      type P = Real
      val keys = map.keys.toList
      val encoder = new Encoder[K] {
        type U = Real
        def wrap(t: K) = map(t)
        def create(acc: List[Variable]): (Real, List[Variable]) = {
          val v = new Variable
          val r = Lookup(v, keys.map { k =>
            map(k)
          })
          (r, v :: acc)
        }
        def extract(t: K, acc: List[Double]): List[Double] =
          keys.indexOf(t).toDouble :: acc
      }
      def create(p: Real) = fn(p)
    }

  trait From[X, U] {
    def from[L](fn: U => L): Predictor[X, L]
    def fromVector[L](k: Int)(fn: IndexedSeq[U] => L): Predictor[Seq[X], L]
  }

  trait EncoderFrom[X, U] extends From[X, U] {
    override def from[L](fn: U => L): EncoderPredictor[X, L]
    override def fromVector[L](k: Int)(
        fn: IndexedSeq[U] => L): EncoderPredictor[Seq[X], L]
  }

  def apply[X](implicit enc: Encoder[X]): EncoderFrom[X, enc.U] =
    new EncoderFrom[X, enc.U] {
      def from[L](fn: enc.U => L): EncoderPredictor[X, L] =
        new EncoderPredictor[X, L] {
          type P = enc.U
          val encoder: Encoder.Aux[X, enc.U] = enc
          def create(p: P) = fn(p)
        }
      def fromVector[L](k: Int)(
          fn: IndexedSeq[enc.U] => L): EncoderPredictor[Seq[X], L] = {
        val vecEnc = Encoder.vector[X](k)
        new EncoderPredictor[Seq[X], L] {
          type P = IndexedSeq[enc.U]
          val encoder = vecEnc
          def create(p: P) = fn(p)
        }
      }
    }
}
