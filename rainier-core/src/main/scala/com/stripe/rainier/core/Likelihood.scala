package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[T] {
  def real: Real
  def variables: List[Variable]
  def extract(t: T): List[Double]

  def fit(seq: Seq[T]): RandomVariable[Unit] = {
    val arrayBufs =
      variables.map { _ =>
        new ArrayBuffer[Double]
      }
    seq.foreach { t =>
      val doubles = extract(t)
      arrayBufs.zip(doubles).foreach {
        case (a, d) => a += d
      }
    }
    val placeholdersMap =
      variables.zip(arrayBufs.map(_.toArray)).toMap
    val target = new Target(real, placeholdersMap)
    new RandomVariable((), Set(target))
  }
}

object Likelihood {
  //
}

trait ToLikelihood[L, T] {
  def apply(l: L): Likelihood[T]
}
