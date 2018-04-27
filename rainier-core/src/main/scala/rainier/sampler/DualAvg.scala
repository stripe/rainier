package rainier.sampler

import scala.annotation.tailrec

private case class DualAvg(
    delta: Double,
    lambda: Double,
    logStepSize: Double,
    logStepSizeBar: Double,
    acceptanceProb: Double,
    avgAcceptanceProb: Double,
    iteration: Int,
    shrinkageTarget: Double,
    stepSizeUpdateDenom: Double = 0.05,
    acceptanceProbUpdateDenom: Int = 10,
    decayRate: Double = 0.75
) {
  val stepSize = Math.exp(logStepSize)
  val finalStepSize = Math.exp(logStepSizeBar)
  val nSteps = (lambda / Math.exp(logStepSize)).toInt.max(1)

  def update(newAcceptanceProb: Double): DualAvg = {
    val newIteration = iteration + 1
    val avgAcceptanceProbMultiplier =
      1.0 / (newIteration + acceptanceProbUpdateDenom)
    val stepSizeMultiplier = Math.pow(newIteration, -decayRate)
    val newAvgAcceptanceProb = (
      (1.0 - avgAcceptanceProbMultiplier) * avgAcceptanceProb
        + (avgAcceptanceProbMultiplier * (delta - newAcceptanceProb))
    )

    val newLogStepSize = (
      shrinkageTarget
        - (newAvgAcceptanceProb * Math.sqrt(newIteration) / stepSizeUpdateDenom)
    )

    val newLogStepSizeBar = (stepSizeMultiplier * newLogStepSize
      + (1.0 - stepSizeMultiplier) * logStepSizeBar)

    copy(
      iteration = newIteration,
      acceptanceProb = newAcceptanceProb,
      avgAcceptanceProb = newAvgAcceptanceProb,
      logStepSize = newLogStepSize,
      logStepSizeBar = newLogStepSizeBar
    )
  }
}

private object DualAvg {
  def apply(delta: Double, lambda: Double, stepSize: Double): DualAvg =
    DualAvg(
      delta = delta,
      lambda = lambda,
      logStepSize = Math.log(stepSize),
      logStepSizeBar = 0.0,
      acceptanceProb = 1.0,
      avgAcceptanceProb = 0.0,
      iteration = 0,
      shrinkageTarget = Math.log(10 * stepSize)
    )

  def findStepSize(chain: HamiltonianChain,
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
}
