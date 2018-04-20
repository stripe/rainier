package rainier.sampler

import scala.annotation.tailrec
import rainier.compute._

sealed trait SampleMethod
case object SampleNUTS extends SampleMethod
case object SampleHMC extends SampleMethod

case class DualAvg(
    delta: Double,
    lambda: Double,
    logEpsilon: Double,
    logEpsilonBar: Double,
    acceptanceProb: Double,
    hBar: Double,
    iteration: Int,
    mu: Double,
    gamma: Double = 0.05,
    t: Int = 10,
    kappa: Double = 0.75
) {
  val stepSize = Math.exp(logEpsilon)
  val nSteps = (lambda / Math.exp(logEpsilon)).toInt.max(1)
}

object DualAvg {
  def apply(delta: Double, lambda: Double, epsilon: Double): DualAvg =
    DualAvg(
      delta = delta,
      lambda = lambda,
      logEpsilon = Math.log(epsilon),
      logEpsilonBar = 0.0,
      acceptanceProb = 1.0,
      hBar = 0.0,
      iteration = 0,
      mu = Math.log(10 * epsilon)
    )
}

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
    val stepSize = findReasonableStepSize(tuned)
    0.until(chains).iterator.flatMap { i =>
      take(tuned, iterations, initialStepSize, sampleMethod).map { c =>
        val eval = new Evaluator(c.variables.zip(c.hParams.qs).toMap)
        Sample(i, c.accepted, eval)
      }.iterator
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

  private def updateDualAvg(acceptanceProb: Double,
                            dualAvg: DualAvg): DualAvg = {
    val newAcceptanceProb = acceptanceProb
    val newIteration = dualAvg.iteration + 1
    val hBarMultiplier = 1.0 / (newIteration + dualAvg.t)
    val newHBar = {
      (1.0 - hBarMultiplier) * dualAvg.hBar
      +(hBarMultiplier * (dualAvg.delta - newAcceptanceProb))
    }
    val newLogEpsilon =
      dualAvg.mu - (newHBar * Math.sqrt(newIteration) / dualAvg.gamma)
    val newLogEpsilonBar = {
      Math.pow(newIteration, -dualAvg.kappa) * newLogEpsilon
      +((1.0 - Math.pow(newIteration, -dualAvg.kappa)) * dualAvg.logEpsilonBar)
    }
    dualAvg.copy(iteration = newIteration,
                 acceptanceProb = newAcceptanceProb,
                 hBar = newHBar,
                 logEpsilon = newLogEpsilon,
                 logEpsilonBar = newLogEpsilonBar)
  }

  private def dualAvgStepSize(chain: HamiltonianChain,
                              delta: Double,
                              lambda: Double,
                              iterations: Int): Double = {
    val epsilon0 = findReasonableStepSize(chain)
    val dualAvg = DualAvg(delta, lambda, epsilon0)
    def go(chain: HamiltonianChain,
           dualAvg: DualAvg,
           remaining: Int): (HamiltonianChain, DualAvg) = {
      if (remaining > 0) {
        val nextChain = chain.nextHMC(dualAvg.stepSize, dualAvg.nSteps)
        val nextAcceptanceProb = nextChain.acceptanceProb
        val nextDualAvg = updateDualAvg(nextAcceptanceProb, dualAvg)
        go(nextChain, nextDualAvg, remaining - 1)
      } else (chain, dualAvg)
    }
    val (_, finalDualAvg) = go(chain, dualAvg, iterations)
    Math.exp(finalDualAvg.logEpsilonBar)
  }
}
