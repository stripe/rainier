package com.stripe.rainier.sampler

import com.stripe.rainier.compute._
import scala.collection.mutable.ListBuffer

final case class HMC(nSteps: Int) extends Sampler {
  def sample(density: Real,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val chain = HamiltonianChain(density.variables, density)
    val stepSize =
      DualAvg.findStepSize(chain, 0.65, nSteps, warmupIterations)
    val buf = new ListBuffer[Array[Double]]
    var i = 0
    while (i < iterations) {
      chain.step(stepSize, nSteps)
      if (i % keepEvery == 0)
        buf += chain.variables
      i += 1
    }
    buf.toList
  }
}
