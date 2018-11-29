package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

trait Likelihood[L, T] {
  def apply(l: L): (Real, Likelihood.Extractor[T])
  def target(l: L, seq: Seq[T]): Target = {
    val (real, extractor) = apply(l)
    val arrayBufs =
      extractor.variables.map { _ =>
        new ArrayBuffer[Double]
      }
    seq.foreach { t =>
      val doubles = extractor.extract(t)
      arrayBufs.zip(doubles).foreach {
        case (a, d) => a += d
      }
    }
    val placeholdersMap =
      extractor.variables.zip(arrayBufs.map(_.toArray)).toMap
    new Target(real, placeholdersMap)
  }
}

object Likelihood {
  trait Extractor[T] {
    def variables: List[Variable]
    def extract(t: T): List[Double]
  }
}
