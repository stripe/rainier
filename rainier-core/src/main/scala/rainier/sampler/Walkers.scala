package rainier.sampler

import rainier.compute._

case class Walkers(walkers: Int) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] =
    WalkersChain(density, density.variables, walkers).toStream
      .drop(warmupIterations)
      .map { c =>
        Sample(c.accepted, c.variables)
      }
}
