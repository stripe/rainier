package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[T] {
  def real: Real
  def placeholders: List[Variable]
  def extract(t: T): List[Double]

  def fit(value: T): RandomVariable[Unit] = {
    val doubles = extract(value)
    val map =
      placeholders.zip(doubles).map { case (p, d) => p -> Array(d) }.toMap
    val density = new Target(real, map).inlined
    RandomVariable.fromDensity(density)
  }

  def fit(seq: Seq[T]): RandomVariable[Unit] = {
    val arrayBufs =
      placeholders.map { _ =>
        new ArrayBuffer[Double]
      }
    seq.foreach { t =>
      val doubles = extract(t)
      arrayBufs.zip(doubles).foreach {
        case (a, d) => a += d
      }
    }
    val placeholdersMap =
      placeholders.zip(arrayBufs.map(_.toArray)).toMap
    val target = new Target(real, placeholdersMap)
    new RandomVariable((), Set(target))
  }
}

object Likelihood {
  implicit def toLikelihood[T, L <: Likelihood[T]]: ToLikelihood[L, T] =
    new ToLikelihood[L, T] {
      def apply(l: L) = l
    }

  trait From[T, U] {
    def from(fn: U => Real): Likelihood[T]
    def fromVector(k: Int)(fn: IndexedSeq[U] => Real): Likelihood[Seq[T]]
  }

  def apply[T](implicit enc: Encoder[T]) =
    new From[T, enc.U] {
      def from(fn: enc.U => Real) = {
        val (p, vs) = enc.create(Nil)
        new Likelihood[T] {
          val real = fn(p)
          val placeholders = vs
          def extract(t: T) = enc.extract(t, Nil)
        }
      }
      def fromVector(k: Int)(fn: IndexedSeq[enc.U] => Real) = {
        val vecEnc = Encoder.vector[T](k)
        val (p, vs) = vecEnc.create(Nil)
        new Likelihood[Seq[T]] {
          val real = fn(p)
          val placeholders = vs
          def extract(t: Seq[T]) = vecEnc.extract(t, Nil)
        }
      }
    }
}

trait ToLikelihood[L, T] {
  def apply(l: L): Likelihood[T]
}
