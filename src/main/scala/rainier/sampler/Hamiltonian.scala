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

  private def computeDeltaH(currentChain: HamiltonianChain,
                            nextChain: HamiltonianChain): Double =
    nextChain.hParams.hamiltonian - currentChain.hParams.hamiltonian

  private def computeExponent(deltaH: Double): Double = {
    val indicator = if (deltaH > Math.log(0.5)) { 1.0 } else { 0.0 }
    2 * indicator - 1
  }

  private def updateEpsilon(deltaH: Double,
                            epsilon: Double,
                            exponent: Double): Double = {
    epsilon * Math.pow(2, exponent)
  }

  private def continuationCriterion(deltaH: Double,
                                    exponent: Double): Boolean = {
    exponent * deltaH > (-exponent) * Math.log(2)
  }

  private def tuningStep(
      chain: HamiltonianChain,
      nextChain: HamiltonianChain,
      epsilon: Double,
      continue: Boolean
  ): (HamiltonianChain, HamiltonianChain, Double, Boolean) = {
    val deltaH = computeDeltaH(chain, nextChain)
    val newExponent = computeExponent(deltaH)
    val newContinue = continuationCriterion(deltaH, newExponent)
    val newEpsilon = updateEpsilon(deltaH, epsilon, newExponent)
    val newNextChain = chain.nextChain(newEpsilon)
    (chain, newNextChain, newEpsilon, newContinue)
  }

  private def findReasonableEpsilon(chain: HamiltonianChain)(
      implicit rng: RNG): Double = {
    val initialEpsilon = 1.0
    val nextChain = chain.nextChain(initialEpsilon)
    val tuningSteps = Stream.iterate((chain, nextChain, initialEpsilon, true)) {
      case (chain, nextChain, eps, continue) =>
        tuningStep(chain, nextChain, eps, continue)
    }
    val (_, _, epsilon, _) = tuningSteps.takeWhile {
      case (chain, nextChain, eps, continue) => continue
    }.last
    epsilon
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
