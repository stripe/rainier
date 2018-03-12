package rainier.sampler

import rainier.compute._

case class Emcee(iterations: Int, burnIn: Int, walkers: Int) extends Sampler {
  val description = ("Emcee",
                     Map(
                       "Walkers" -> walkers.toDouble,
                       "Iterations" -> iterations.toDouble,
                       "Burn In" -> burnIn.toDouble
                     ))

  def sample(density: Real)(implicit rng: RNG): Iterator[Sample] =
    EmceeChain(density, walkers).toStream
      .drop(burnIn * walkers)
      .take(iterations * walkers)
      .map { c =>
        Sample(c.walker, c.accepted, new Evaluator(c.variables))
      }
      .iterator
}
