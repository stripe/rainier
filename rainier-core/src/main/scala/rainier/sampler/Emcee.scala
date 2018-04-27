package rainier.sampler

import rainier.compute._

case class Emcee(walkers: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] =
    EmceeChain(density, density.variables, walkers).toStream
      .drop(warmupIterations)
      .map { c =>
        val map = density.variables.zip(c.variables).toMap
        Sample(c.accepted, new Evaluator(map))
      }
}
