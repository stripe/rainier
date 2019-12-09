package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Sample(chains: List[List[Array[Double]]], model: Model)(
    implicit rng: RNG) {
  def diagnostics = Sampler.diagnostics(chains)

  def predict[T, U](value: T)(implicit tg: ToGenerator[T, U]): List[U] = {
    val fn = tg(value).prepare(model.parameters)
    chains.flatMap { c =>
      c.map { a =>
        fn(a)
      }
    }
  }
}
