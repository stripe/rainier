package rainier.sampler

import rainier.compute._

case class NUTS(stepSize: Double = 1.0, maxDepth: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] =
    toStream(density, HamiltonianChain(density.variables, density))

  private def toStream(density: Real,
                       chain: HamiltonianChain): Stream[Sample] = {
    val eval = new Evaluator(density.variables.zip(chain.hParams.qs).toMap)
    Sample(chain.accepted, eval) #:: toStream(
      density,
      chain.nextNUTS(stepSize, maxDepth))
  }
}
