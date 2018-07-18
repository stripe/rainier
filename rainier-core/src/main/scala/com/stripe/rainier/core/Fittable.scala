package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.language.implicitConversions
import scala.collection.mutable.ArrayBuffer

sealed trait Fittable[L, T] {
  def target(pdf: L, value: T): Target
}

object Fittable {
  case class Ops[L, T](pdf: L, f: Fittable[L, T]) {
    def fit(value: T): RandomVariable[L] =
      new RandomVariable(pdf, Set(f.target(pdf, value)))
  }

  implicit def ops[L, T](pdf: L)(implicit f: Fittable[L, T]) =
    Ops(pdf, f)
}

private final case class SimpleFittable[L, T](lh: Likelihood[L, T])
    extends Fittable[L, T] {
  def target(pdf: L, value: T) =
    Target(lh.logDensity(pdf, value))
}

private final case class SeqFittable[L, T](lh: Likelihood[L, T])
    extends Fittable[L, Seq[T]] {
  def target(pdf: L, value: Seq[T]) =
    Target(Real.sum(value.map { t =>
      lh.logDensity(pdf, t)
    }))
}

private final case class PlaceholderFittable[L, T, U](
    lh: PlaceholderLikelihood[L, T, U],
    ph: Placeholder[T, U]
) extends Fittable[L, T] {

  def target(pdf: L, value: T) =
    Target(lh.logDensity(pdf, ph.wrap(value)))
}

private final case class PlaceholderSeqFittable[L, T, U](
    lh: PlaceholderLikelihood[L, T, U],
    ph: Placeholder[T, U]
) extends Fittable[L, Seq[T]] {
  def target(pdf: L, value: Seq[T]) = {
    val (placeholder, variables) = ph.create()
    val real = lh.logDensity(pdf, placeholder)
    val arrayBufs =
      variables.map { _ =>
        new ArrayBuffer[Double]
      }
    value.foreach { t =>
      val doubles = ph.extract(t, Nil)
      arrayBufs.zip(doubles).foreach {
        case (a, d) => a += d
      }
    }
    val placeholdersMap =
      variables.zip(arrayBufs.map(_.toArray)).toMap
    new Target(real, placeholdersMap)
  }
}
