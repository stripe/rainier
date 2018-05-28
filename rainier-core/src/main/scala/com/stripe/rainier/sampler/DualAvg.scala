package com.stripe.rainier.sampler

import scala.annotation.tailrec

final private case class DualAvg(
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
  val stepSize: Double = Math.exp(logStepSize)
  val finalStepSize: Double = Math.exp(logStepSizeBar)

  def update(logAcceptanceProb: Double): DualAvg = {
    val newAcceptanceProb = Math.exp(logAcceptanceProb)
    val newIteration = iteration + 1
    val avgAcceptanceProbMultiplier =
      1.0 / (newIteration.toDouble + acceptanceProbUpdateDenom)
    val stepSizeMultiplier = Math.pow(newIteration.toDouble, -decayRate)
    val newAvgAcceptanceProb = (
      (1.0 - avgAcceptanceProbMultiplier) * avgAcceptanceProb
        + (avgAcceptanceProbMultiplier * (delta - newAcceptanceProb))
    )

    val newLogStepSize = (
      shrinkageTarget
        - (newAvgAcceptanceProb * Math.sqrt(newIteration.toDouble) / stepSizeUpdateDenom)
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

  def findStepSize(chain: HamiltonianChain,
                   delta: Double,
                   nSteps: Int,
                   iterations: Int): Double = {
    val stepSize0 = findReasonableStepSize(chain.clone)
    val dualAvg = DualAvg(delta, nSteps, stepSize0)
    def go(dualAvg: DualAvg, remaining: Int): DualAvg = {
      if (remaining > 0) {
        val logAcceptanceProb = chain.step(dualAvg.stepSize, dualAvg.nSteps)
        val nextDualAvg = dualAvg.update(logAcceptanceProb)
        go(nextDualAvg, remaining - 1)
      } else dualAvg
    }
    val finalDualAvg = go(dualAvg, iterations)
    finalDualAvg.finalStepSize
  }

  private def computeExponent(logAcceptanceProb: Double): Double =
    if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def updateStepSize(stepSize: Double, exponent: Double): Double =
    stepSize * Math.pow(2, exponent)

  private def continueTuningStepSize(logAcceptanceProb: Double,
                                     exponent: Double): Boolean =
    !logAcceptanceProb.isNegInfinity &&
      (exponent * logAcceptanceProb > -exponent * Math.log(2))

  @tailrec
  private def tuneStepSize(
      chain: HamiltonianChain,
      exponent: Double,
      logAcceptanceProb: Double,
      stepSize: Double
  ): Double = {
    if (continueTuningStepSize(logAcceptanceProb, exponent)) {
      val newStepSize = updateStepSize(stepSize, exponent)
      val newLogAcceptanceProb = chain.stepOnce(newStepSize)
      tuneStepSize(chain, exponent, newLogAcceptanceProb, newStepSize)
    } else { stepSize }
  }

  private def findReasonableStepSize(chain: HamiltonianChain): Double = {
    val initialStepSize = 1.0
    val logAcceptanceProb = chain.stepOnce(initialStepSize)
    val exponent = computeExponent(logAcceptanceProb)
    tuneStepSize(chain, exponent, logAcceptanceProb, initialStepSize)
  }
}
