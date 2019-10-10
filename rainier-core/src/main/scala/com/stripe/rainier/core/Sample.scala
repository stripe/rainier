package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

case class Sample[+T](chains: List[List[Array[Double]]],
                      targets: Set[Target],
                      variables: List[Variable],
                      value: T) {
  def map[U](fn: T => U) = Sample(chains, targets, variables, fn(value))

  def diagnostics = Sampler.diagnostics(chains)

  def waic =
    targets
      .map { t =>
        WAIC(chains.flatten, variables, t)
      }
      .reduce(_ + _)

  def toList[V](implicit tg: ToGenerator[T, V], rng: RNG): List[V] = {
    val fn = tg(value).prepare(variables)
    chains.flatMap { c =>
      c.map { a =>
        fn(a)
      }
    }
  }
}
