package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[-L, T] {
  type V
  def wrap(pdf: L, value: T): V
  def placeholder(pdf: L): Placeholder[T, V]
  def logDensity(pdf: L, value: V): Real

  def target(pdf: L, value: T): Target =
    Target(logDensity(pdf, wrap(pdf, value)))

  def sequence(pdf: L, seq: Seq[T]): Target = {
    val ph = placeholder(pdf)
    val real = logDensity(pdf, ph.value)
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
      implicit m: Mapping[T, U]): Likelihood[L, T] =
    new Likelihood[L, T] {
      type V = U
      def wrap(pdf: L, value: T) = m.wrap(value)
      def placeholder(pdf: L) = m.placeholder
      def logDensity(pdf: L, value: V) = fn(pdf, value)
    }
}
