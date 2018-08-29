package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[T] {
  type F

  private[core] type P
  private[core] def wrap(value: T): P
  private[core] def placeholder(): Placeholder[T, P]
  private[core] def logDensity(value: P): Real

  def target(value: T): Target =
    Target(logDensity(wrap(value)))

  def sequence(seq: Seq[T]): Target = {
    val ph = placeholder()
    val real = logDensity(ph.value)
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
  case class Ops[T, L](pdf: L)(implicit ev: L <:< Likelihood[T]) {
    def fit(value: T): RandomVariable[L] = RandomVariable.fit(pdf, value)
    def fit(seq: Seq[T]): RandomVariable[L] = RandomVariable.fit(pdf, seq)
  }

  implicit def ops[T, L](pdf: L)(implicit ev: L <:< Likelihood[T]) =
    Ops(pdf)
}
