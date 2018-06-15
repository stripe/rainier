package com.stripe.rainier.core

import com.stripe.rainier.sampler._

final class Data[T] private (val list: List[Option[T]], val batchSize: Int) {
  def flatMap[U](fn: T => Option[U]) =
    new Data(list.map { x =>
      x.flatMap(fn)
    }, batchSize)
  def map[U](fn: T => U) = flatMap { t =>
    Some(fn(t))
  }
  def filter(fn: T => Boolean) = flatMap { t =>
    if (fn(t)) Some(t) else None
  }
  val numBatches = list.size / batchSize
}

object Data {
  def apply[T](list: List[T], batchSize: Int) =
    new Data(list.map { t =>
      Some(t)
    }, batchSize)
}

trait Observable[T] {
  def numVariables: Int
  def extract(t: T): List[Double]
  def observe(data: Data[T]) = {
    val columns =
      1.to(data.batchSize * (numVariables + 1))
        .map { _ =>
          new Array[Double](data.numBatches)
        }
        .toArray
    data.list.zipWithIndex.foreach {
      case (Some(t), i) => ???

      case (None, i) => ???

    }
    new Observations(columns, numVariables)
  }
}

object Observable {
  implicit def numeric[T](implicit num: Numeric[T]): Observable[T] =
    new Observable[T] {
      val numVariables = 1
      def extract(t: T) = List(num.toDouble(t))
    }

  implicit def zip[T, U](implicit ot: Observable[T],
                         ou: Observable[U]): Observable[(T, U)] =
    new Observable[(T, U)] {
      val numVariables = ot.numVariables + ou.numVariables
      def extract(t: (T, U)) = ot.extract(t._1) ++ ou.extract(t._2)
    }
}
