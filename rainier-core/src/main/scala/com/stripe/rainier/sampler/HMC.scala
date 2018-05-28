package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class HMC(nSteps: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int, iterations: Int)(
      implicit rng: RNG): List[Array[Double]] = {
    val (tunedChain, tunedStepSize) =
      DualAvg.findStepSize(HamiltonianChain(density.variables, density),
                           0.65,
                           nSteps,
                           warmupIterations)
    1.to(iterations)
      .scanLeft(tunedChain) {
        case (chain, _) =>
          chain.nextHMC(tunedStepSize, nSteps)
      }
      .map(_.variables)
      .toList
  }
}
