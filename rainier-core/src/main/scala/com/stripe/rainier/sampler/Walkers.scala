package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class Walkers(walkers: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] =
    WalkersChain(density, density.variables, walkers).toStream
      .drop(warmupIterations)
      .map { c =>
        Sample(c.accepted, c.variables)
      }
}
