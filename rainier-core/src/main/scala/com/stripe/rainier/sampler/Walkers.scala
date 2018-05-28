package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class Walkers(walkers: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int, iterations: Int)(
      implicit rng: RNG): List[Array[Double]] = {
    val initial = WalkersChain(density, density.variables, walkers)
    val warmedUp =
      1.to(warmupIterations)
        .foldLeft(initial) {
          case (chain, _) =>
            chain.next
        }
    1.to(iterations)
      .scanLeft(warmedUp) {
        case (chain, _) =>
          chain.next
      }
      .map(_.variables)
      .toList
  }
}
