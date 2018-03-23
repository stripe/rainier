package rainier.sampler

import rainier.compute._

sealed trait SampleMethod
case object SampleNUTS extends SampleMethod
case object SampleHMC extends SampleMethod

case class Hamiltonian(iterations: Int,
                       burnIn: Int,
                       tuneEvery: Int,
                       nSteps: Int,
                       sampleMethod: SampleMethod = SampleNUTS,
                       chains: Int = 4,
                       initialStepSize: Double = 1.0)
    extends Sampler {
  val description = ("HamiltonianMC",
                     Map(
                       "nSteps" -> nSteps.toDouble,
                       "initialStepSize" -> initialStepSize,
                       "iterations" -> iterations.toDouble,
                       "burnIn" -> burnIn.toDouble,
                       "tuneEvery" -> tuneEvery.toDouble,
                       "chains" -> chains.toDouble
                     ))

  def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    val tuned = take(HamiltonianChain(density),
                     burnIn + 1,
                     initialStepSize,
                     sampleMethod).last
    0.until(chains).iterator.flatMap { i =>
      take(tuned, iterations, initialStepSize, sampleMethod).map { c =>
        val eval = new Evaluator(c.variables.zip(c.hParams.qs).toMap)
        Sample(i, c.accepted, eval)
      }.iterator
    }
  }

  private def take(chain: HamiltonianChain,
                   iterations: Int,
                   stepSize: Double,
                   sampleMethod: SampleMethod): List[HamiltonianChain] = {
    def go(list: List[HamiltonianChain],
           remaining: Int): List[HamiltonianChain] =
      (remaining, sampleMethod) match {
        case (n, SampleNUTS) if n > 0 =>
          go(list.head.nextNUTS(stepSize) :: list, n - 1)
        case (n, SampleHMC) if n > 0 =>
          go(list.head.nextHMC(stepSize, nSteps) :: list, n - 1)
        case _ => list.take(iterations)
      }
    go(List(chain), iterations)
  }
}
