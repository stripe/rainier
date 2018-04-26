package rainier.sampler

import rainier.compute._

case class NUTS(maxDepth: Int = 6, initialStepSize: Double = 1.0)
    extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] = ???
}
