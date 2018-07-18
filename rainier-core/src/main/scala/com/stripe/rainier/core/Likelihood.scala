package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[L, T] {
  type V
  def placeholder(pdf: L): Placeholder[T, V]
  def logDensity(pdf: L, value: V): Real

  def target(pdf: L, value: T): Target =
    Target(logDensity(pdf, placeholder(pdf).wrap(value)))

  def sequence(pdf: L, seq: Seq[T]): Target = {
    val ph = placeholder(pdf)
    val real = logDensity(pdf, ph.placeholder)
    val variables = ph.variables
    val arrayBufs =
      variables.map { _ =>
        new ArrayBuffer[Double]
      }
    seq.foreach { t =>
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

object Likelihood {
  def from[L, T, U](fn: (L, U) => Real)(
      implicit ph: Placeholder[T, U]): Likelihood[L, T] =
    new Likelihood[L, T] {
      type V = U
      def placeholder(pdf: L) = ph
      def logDensity(pdf: L, value: V) = fn(pdf, value)
    }
}
