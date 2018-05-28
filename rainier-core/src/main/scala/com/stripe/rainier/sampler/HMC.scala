package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class HMC(nSteps: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int, iterations: Int)(
      implicit rng: RNG): List[Array[Double]] = {
    val chain = HamiltonianChain(density.variables, density)
    val stepSize =
      DualAvg.findStepSize(chain, 0.65, nSteps, warmupIterations)
    1.to(iterations)
      .toList
      .map { _ =>
        chain.step(stepSize, nSteps)
        chain.variables
      }
  }
}
