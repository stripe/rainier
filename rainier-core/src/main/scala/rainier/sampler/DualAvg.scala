package rainier.sampler

import scala.annotation.tailrec

private case class DualAvg(
    delta: Double,
    nSteps: Int,
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
  def apply(delta: Double, nSteps: Int, stepSize: Double): DualAvg =
    DualAvg(
      delta = delta,
      nSteps = nSteps,
      logStepSize = Math.log(stepSize),
      logStepSizeBar = 0.0,
      acceptanceProb = 1.0,
      avgAcceptanceProb = 0.0,
      iteration = 0,
      shrinkageTarget = Math.log(10 * stepSize)
    )

  def findStepSize(
      chain: HamiltonianChain,
      delta: Double,
      nSteps: Int,
      iterations: Int)(implicit rng: RNG): (HamiltonianChain, Double) = {
    val stepSize0 = findReasonableStepSize(chain)
    val dualAvg = DualAvg(delta, nSteps, stepSize0)
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

  private def computeExponent(logAcceptanceProb: Double): Double =
    if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def updateStepSize(stepSize: Double, exponent: Double): Double =
    stepSize * Math.pow(2, exponent)

  private def continueTuningStepSize(logAcceptanceProb: Double,
                                     exponent: Double): Boolean =
    exponent * logAcceptanceProb > -exponent * Math.log(2)

  @tailrec
  private def tuneStepSize(
      chain: HamiltonianChain,
      nextChain: HamiltonianChain,
      exponent: Double,
      stepSize: Double
  ): Double = {
    val logAcceptanceProb = chain.logAcceptanceProb(nextChain)
    if (continueTuningStepSize(logAcceptanceProb, exponent)) {
      val newStepSize = updateStepSize(stepSize, exponent)
      val newNextChain = chain.stepOnce(newStepSize)
      tuneStepSize(chain, newNextChain, exponent, newStepSize)
    } else { stepSize }
  }

  private def findReasonableStepSize(chain: HamiltonianChain): Double = {
    val initialStepSize = 1.0
    val nextChain = chain.stepOnce(initialStepSize)
    val logAcceptanceProb = chain.logAcceptanceProb(nextChain)
    val exponent = computeExponent(logAcceptanceProb)
    tuneStepSize(chain, nextChain, exponent, initialStepSize)
  }
}
