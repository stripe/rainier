package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Sample(chains: List[List[Array[Double]]], model: Model)(
    implicit rng: RNG) {
  def diagnostics = Sampler.diagnostics(chains)

  def thin(n: Int): Sample = {
    val newChains =
      chains
        .map { c =>
          c.zipWithIndex
            .filter { case (_, i) => i % n == 0 }
            .map(_._1)
        }
    Sample(newChains, model)
  }

  def predict[T, U](value: T)(implicit tg: ToGenerator[T, U]): List[U] = {
    val fn = tg(value).prepare(model.parameters)
    chains.flatMap { c =>
      c.map { a =>
        fn(a)
      }
    }
  }
}
