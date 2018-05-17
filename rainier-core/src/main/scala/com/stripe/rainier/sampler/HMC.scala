package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

case class HMC(nSteps: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] = {
    val (tunedChain, tunedStepSize) =
      DualAvg.findStepSize(HamiltonianChain(density.variables, density),
                           0.65,
                           nSteps,
                           warmupIterations)
    toStream(density, tunedChain, tunedStepSize)
  }

  private def toStream(density: Real,
                       chain: HamiltonianChain,
                       stepSize: Double)(implicit rng: RNG): Stream[Sample] =
    Sample(chain.accepted, chain.variables) #:: toStream(
      density,
      chain.nextHMC(stepSize, nSteps),
      stepSize)
}
