package rainier.sampler

import scala.annotation.tailrec
import rainier.compute._

sealed trait SampleMethod
case object SampleNUTS extends SampleMethod
case object SampleHMC extends SampleMethod

case class Hamiltonian(nSteps: Int,
                       sampleMethod: SampleMethod = SampleNUTS,
                       initialStepSize: Double = 1.0)
    extends Sampler {
  def sample(density: Real, warmupIterations: Int)(implicit rng: RNG): Stream[Sample] =
    val (tunedChain, tunedStepSize) =
      dualAvgStepSize(HamiltonianChain(density.variables, density),
                      0.65,
                      nSteps * initialStepSize,
                      warmupIterations)
    take(tunedChain, iterations, tunedStepSize, sampleMethod).map { c =>
      val eval = new Evaluator(density.variables.zip(c.hParams.qs).toMap)
      Sample(i, c.accepted, eval)
    }
  }

  /**
    * @note: Let U(Θ) be the potential, K(r) the kinetic.
    * The NUTS paper defines
    * H(Θ,r) = U(Θ) - K(r) as the difference
    * p(Θ,r) = exp(H)
    * and for ΔH = H(Θ',r') - H(Θ,r)
    * defines the acceptance ratio as min{1, exp(ΔH)}.
    * Neal and McKay, on the other hand, define
    * H(Θ,r) = U(Θ) + K(r) as the sum
    * and the acceptance ratio as min{1, exp(-ΔH)}.
    * These are the definitions we use in the rest of HMC and NUTS
    * so we similarly use -ΔH to tune the stepSize here.
    */
  private def computeDeltaH(chain: HamiltonianChain,
                            nextChain: HamiltonianChain): Double =
    nextChain.hParams.hamiltonian - chain.hParams.hamiltonian

  private def computeExponent(deltaH: Double): Double =
    if (-deltaH > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def updateStepSize(stepSize: Double, exponent: Double): Double =
    stepSize * Math.pow(2, exponent)

  private def continueTuningStepSize(deltaH: Double,
                                     exponent: Double): Boolean =
    exponent * (-deltaH) > -exponent * Math.log(2)

  @tailrec
  private def tuneStepSize(
      chain: HamiltonianChain,
      nextChain: HamiltonianChain,
      exponent: Double,
      stepSize: Double
  ): Double = {
    val deltaH = computeDeltaH(chain, nextChain)
    if (continueTuningStepSize(deltaH, exponent)) {
      val newStepSize = updateStepSize(stepSize, exponent)
      val newNextChain = chain.stepOnce(newStepSize)
      tuneStepSize(chain, newNextChain, exponent, newStepSize)
    } else { stepSize }
  }

  private def findReasonableStepSize(chain: HamiltonianChain): Double = {
    val initialStepSize = 1.0
    val nextChain = chain.stepOnce(initialStepSize)
    val deltaH = computeDeltaH(chain, nextChain)
    val exponent = computeExponent(deltaH)
    tuneStepSize(chain, nextChain, exponent, initialStepSize)
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

  private def dualAvgStepSize(chain: HamiltonianChain,
                              delta: Double,
                              lambda: Double,
                              iterations: Int): (HamiltonianChain, Double) = {
    val stepSize0 = findReasonableStepSize(chain)
    val dualAvg = DualAvg(delta, lambda, stepSize0)
    def go(chain: HamiltonianChain,
           dualAvg: DualAvg,
           remaining: Int): (HamiltonianChain, DualAvg) = {
      if (remaining > 0) {
        val nextChain = chain.nextHMC(dualAvg.stepSize, dualAvg.nSteps)
        val nextAcceptanceProb = nextChain.acceptanceProb
        val nextDualAvg = dualAvg.update(nextAcceptanceProb)
        go(nextChain, nextDualAvg, remaining - 1)
      } else (chain, dualAvg)
    }
    val (tunedChain, finalDualAvg) = go(chain, dualAvg, iterations)
    (tunedChain, finalDualAvg.finalStepSize)
  }
}
