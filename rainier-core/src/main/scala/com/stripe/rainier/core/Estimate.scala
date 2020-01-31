package com.stripe.rainier.core

import com.stripe.rainier.sampler._

case class Estimate(parameters: Array[Double], model: Model) {
  def predict[T, U](value: T)(implicit tg: ToGenerator[T, U], rng: RNG): U = {
    val fn = tg(value).prepare(model.parameters)
    fn(parameters)
  }
}
