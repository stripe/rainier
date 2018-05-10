package rainier.sampler

import rainier.compute._

trait Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample]
}

case class Sample(accepted: Boolean, evaluator: Numeric[Real])

object Sampler {
  object Default {
    val sampler = Walkers(100)
    val iterations = 10000
    val warmupIterations = 10000
  }
}
