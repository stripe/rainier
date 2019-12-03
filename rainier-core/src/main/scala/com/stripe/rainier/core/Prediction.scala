package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Prediction[T](model: Model, generator: Generator[T]) {
  def map[U](fn: T => U): Prediction[U] = Prediction(model, generator.map(fn))
  def flatMap[U, V](fn: T => Generator[U]): Prediction[U] =
    Prediction(model, generator.flatMap(fn))
  def zip[U](other: Prediction[U]): Prediction[(T, U)] =
    Prediction(model.merge(other.model), generator.zip(other.generator))

  def toSample(sampler: Sampler,
               warmupIterations: Int,
               iterations: Int,
               keepEvery: Int = 1,
               nChains: Int = 1)(implicit rng: RNG): Sample[T] = {
    val density = model.density
    val chains = 1.to(nChains).toList.map { _ =>
      sampler.sample(density, warmupIterations, iterations, keepEvery)
    }
    Sample(chains, model, generator)
  }
}
