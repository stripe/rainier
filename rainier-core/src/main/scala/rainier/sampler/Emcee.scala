package rainier.sampler

import rainier.compute._

case class Emcee(walkers: Int) extends Sampler {
  def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    EmceeChain(density, density.variables, walkers).toStream
      .drop(burnIn * walkers)
      .take(iterations * walkers)
      .map { c =>
        val map = density.variables.zip(c.variables).toMap
        Sample(c.walker, c.accepted, new Evaluator(map))
      }
      .iterator
  }
}
