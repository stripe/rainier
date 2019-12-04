package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Sample(chains: List[List[Array[Double]]], model: Model, rng: RNG) {
  def diagnostics = Sampler.diagnostics(chains)

  def waic =
    model.targets
      .map { t =>
        WAIC(chains.flatten, model.variables, t)
      }
      .reduce(_ + _)

  def predict[T, U](value: T)(implicit tg: ToGenerator[T, U]): List[U] = {
    val fn = tg(value).prepare(model.variables)(rng)
    chains.flatMap { c =>
      c.map { a =>
        fn(a)
      }
    }
  }
}
