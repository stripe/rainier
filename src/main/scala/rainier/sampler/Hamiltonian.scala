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

  private def computeExponent(deltaH: Double): Double = {
    if (deltaH > Math.log(0.5)) { 1.0 } else { -1.0 }
  }

  private def updateStepSize(deltaH: Double,
                             stepSize: Double,
                             exponent: Double): Double = {
    stepSize * Math.pow(2, exponent)
  }

  private def continueTuningStepSize(deltaH: Double,
                                     exponent: Double): Boolean = {
    exponent * deltaH > (-exponent) * Math.log(2)
  }

  private def tuneStepSize(
      chain: HamiltonianChain,
      nextChain: HamiltonianChain,
      stepSize: Double,
      continue: Boolean
  ): (HamiltonianChain, HamiltonianChain, Double, Boolean) = {
    val deltaH = nextChain.hParams.hamiltonian - chain.hParams.hamiltonian
    val newExponent = computeExponent(deltaH)
    val newContinue = continueTuningStepSize(deltaH, newExponent)
    val newStepSize = updateStepSize(deltaH, stepSize, newExponent)
    val newNextChain = chain.nextChain(newStepSize)
    (chain, newNextChain, newStepSize, newContinue)
  }

  private def findReasonableStepSize(chain: HamiltonianChain,
                                     initialStepSize: Double): Double = {
    val nextChain = chain.nextChain(initialStepSize)
    val tuningSteps =
      Stream.iterate((chain, nextChain, initialStepSize, true)) {
        case (chain, nextChain, stepSize, continue) =>
          tuneStepSize(chain, nextChain, stepSize, continue)
      }
    val (_, _, stepSize, _) = tuningSteps.takeWhile {
      case (_, _, _, continue) => continue
    }.last
    stepSize
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
