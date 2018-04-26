package rainier.sampler

import rainier.compute._

case class HMC(nSteps: Int, initialStepSize: Double = 1.0) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] = {
    val (tunedChain, tunedStepSize) =
      DualAvg.findStepSize(HamiltonianChain(density.variables, density),
                           0.65,
                           nSteps * initialStepSize,
                           warmupIterations)
    toStream(density, tunedChain, tunedStepSize)
  }

  private def toStream(density: Real,
                       chain: HamiltonianChain,
                       stepSize: Double): Stream[Sample] = {
    val eval = new Evaluator(density.variables.zip(chain.hParams.qs).toMap)
    Sample(chain.accepted, eval) #:: toStream(density,
                                              chain.nextHMC(stepSize, nSteps),
                                              stepSize)
  }
}
