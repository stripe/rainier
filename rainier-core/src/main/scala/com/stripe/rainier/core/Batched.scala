package com.stripe.rainier.core

import com.stripe.rainier.sampler._
import com.stripe.rainier.compute._

final class Batched[T] private (val list: List[Option[T]], val batchSize: Int) {
  def flatMap[U](fn: T => Option[U]) =
    new Batched(list.map { x =>
      x.flatMap(fn)
    }, batchSize)
  def map[U](fn: T => U) = flatMap { t =>
    Some(fn(t))
  }
  def filter(fn: T => Boolean) = flatMap { t =>
    if (fn(t)) Some(t) else None
  }
  val numBatches = list.size / batchSize

  def fit[X](likelihood: X)(
      implicit fittable: BatchFittable[X, T]): RandomVariable[X] = {
    val placeholders = 1.to(batchSize).map { _ =>
      fittable.placeholder.create()
    }
    val markers = placeholders.map { _ =>
      new Variable
    }
    val density = Real.sum(placeholders.zip(markers).map {
      case (u, m) =>
        If(m, fittable.logDensity(likelihood, u), Real.zero)
    })

    val missing = 0.0 :: List.fill(fittable.placeholder.arity)(0.0)
    val rows =
      list.grouped(batchSize).map { batch =>
        batch.flatMap {
          case None => missing
          case Some(t) =>
            1.0 :: fittable.placeholder.values(t)
        }.toArray
      }
    val variables =
      placeholders.zip(markers).flatMap {
        case (u, m) =>
          m :: fittable.placeholder.variables(u)
      }
    val columns = variables.zipWithIndex.map {
      case (v, i) =>
        (v, rows.map { a =>
          a(i)
        }.toArray)
    }
    RandomVariable(likelihood, density, new Batches(columns.toArray))
  }
}

object Batched {
  def apply[T](list: List[T], batchSize: Int) =
    new Batched(list.map { t =>
      Some(t)
    }, batchSize)
}

trait BatchFittable[X, T] {
  type P
  def placeholder: Placeholder[T, P]
  def logDensity(likelihood: X, placeholder: P): Real
}

trait Placeholder[T, U] {
  def create(): U
  def variables(placeholder: U): List[Variable]
  def values(input: T): List[Double]
  def arity: Int
}
