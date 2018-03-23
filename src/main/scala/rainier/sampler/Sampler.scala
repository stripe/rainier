package rainier.sampler

import rainier.compute._

trait Sampler {
  def description: (String, Map[String, Double])
  def sample(density: Real)(implicit rng: RNG): Iterator[Sample]
}

case class Sample(chain: Int, accepted: Boolean, evaluator: Numeric[Real])

object Sampler {
  val default: Sampler = Emcee(1000, 1000, 100)
}
