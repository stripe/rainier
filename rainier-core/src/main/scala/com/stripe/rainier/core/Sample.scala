package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Sample[+T](chains: List[List[Array[Double]]],
                      model: Model,
                      generator: Generator[T]) {
  def map[U](fn: T => U) = Sample(chains, model, generator.map(fn))
  def flatMap[U](fn: T => Generator[U]) =
    Sample(chains, model, generator.flatMap(fn))

  def diagnostics = Sampler.diagnostics(chains)

  def waic =
    model.targets
      .map { t =>
        WAIC(chains.flatten, model.variables, t)
      }
      .reduce(_ + _)

  def toList(implicit rng: RNG): List[T] = {
    val fn = generator.prepare(model.variables)
    chains.flatMap { c =>
      c.map { a =>
        fn(a)
      }
    }
  }
}
