package com.stripe.rainier.sampler

import Log._
import java.util.concurrent.TimeUnit._

final private class DualAvg(
    delta: Double,
    var logStepSize: Double,
    var logStepSizeBar: Double,
    var avgAcceptanceProb: Double,
    var iteration: Int,
    shrinkageTarget: Double,
    stepSizeUpdateDenom: Double = 0.05,
    acceptanceProbUpdateDenom: Int = 10,
    decayRate: Double = 0.75
) {
  def stepSize: Double = Math.exp(logStepSize)
  def finalStepSize: Double = Math.exp(logStepSizeBar)

  def update(logAcceptanceProb: Double): Unit = {
    val newAcceptanceProb = Math.exp(logAcceptanceProb)
    iteration = iteration + 1
    val avgAcceptanceProbMultiplier =
      1.0 / (iteration.toDouble + acceptanceProbUpdateDenom)
    val stepSizeMultiplier = Math.pow(iteration.toDouble, -decayRate)

    avgAcceptanceProb = (
      (1.0 - avgAcceptanceProbMultiplier) * avgAcceptanceProb
        + (avgAcceptanceProbMultiplier * (delta - newAcceptanceProb))
    )

    logStepSize = (
      shrinkageTarget
        - (avgAcceptanceProb * Math.sqrt(iteration.toDouble) / stepSizeUpdateDenom)
    )

    logStepSizeBar = (stepSizeMultiplier * logStepSize
      + (1.0 - stepSizeMultiplier) * logStepSizeBar)

    FINEST.log("warmup iteration %d, avgAcceptanceProb %f, logStepSize %f",
               iteration,
               avgAcceptanceProb,
               logStepSize)
  }
}

private object DualAvg {
  def apply(delta: Double, stepSize: Double): DualAvg =
    new DualAvg(
      delta = delta,
      logStepSize = Math.log(stepSize),
      logStepSizeBar = 0.0,
      avgAcceptanceProb = 0.0,
      iteration = 0,
      shrinkageTarget = Math.log(10 * stepSize)
    )

  def findStepSize(lf: LeapFrog,
                   params: Array[Double],
                   delta: Double,
                   nSteps: Int,
                   iterations: Int)(implicit rng: RNG): Double = {
    FINE.log("Finding reasonable initial step size")
    val stepSize0 = findReasonableStepSize(lf, params)
    FINE.log("Found initial step size of %f", stepSize0)

    if (stepSize0 == 0.0)
      0.0
    else {
      val dualAvg = DualAvg(delta, stepSize0)
      var i = 0
      while (i < iterations) {
        val logAcceptanceProb = lf.step(params, nSteps, dualAvg.stepSize)
        dualAvg.update(logAcceptanceProb)

        FINER
          .atMostEvery(1, SECONDS)
          .log("Warmup iteration %d of %d, stepSize %f, acceptance prob %f",
               i,
               iterations,
               dualAvg.stepSize,
               Math.exp(logAcceptanceProb))

        i += 1
      }
      dualAvg.finalStepSize
    }
  }

  private def computeExponent(logAcceptanceProb: Double): Double =
    if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def continueTuningStepSize(logAcceptanceProb: Double,
                                     exponent: Double): Boolean =
    exponent * logAcceptanceProb > -exponent * Math.log(2)

  private def findReasonableStepSize(lf: LeapFrog,
                                     params: Array[Double]): Double = {
    var stepSize = 1.0
    var logAcceptanceProb = lf.tryStepping(params, stepSize)
    val exponent = computeExponent(logAcceptanceProb)
    val doubleOrHalf = Math.pow(2, exponent)
    while (continueTuningStepSize(logAcceptanceProb, exponent)) {
      stepSize *= doubleOrHalf
      logAcceptanceProb = lf.tryStepping(params, stepSize)

      FINER
        .atMostEvery(1, SECONDS)
        .log("stepSize %f, acceptance prob %f",
             stepSize,
             Math.exp(logAcceptanceProb))
    }
    stepSize
  }
}
