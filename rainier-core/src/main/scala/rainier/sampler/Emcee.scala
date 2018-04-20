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
    val variables = Real.variables(density).toList
    EmceeChain(density, variables, walkers).toStream
      .drop(burnIn * walkers)
      .take(iterations * walkers)
      .map { c =>
        val map = variables.zip(c.variables).toMap
        Sample(c.walker, c.accepted, new Evaluator(map))
      }
      .iterator
  }
}
