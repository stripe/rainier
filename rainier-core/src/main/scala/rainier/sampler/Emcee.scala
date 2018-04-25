package rainier.sampler

import rainier.compute._

case class Emcee(iterations: Int, burnIn: Int, walkers: Int) extends Sampler {
  val description = ("Emcee",
                     Map(
                       "Walkers" -> walkers.toDouble,
                       "Iterations" -> iterations.toDouble,
                       "Burn In" -> burnIn.toDouble
                     ))

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
