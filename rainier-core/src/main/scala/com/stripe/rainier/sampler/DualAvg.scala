package com.stripe.rainier.sampler

import Log._
import java.util.concurrent.TimeUnit._

final private class DualAvg(
    delta: Double,
    var logStepSize: Double,
    var logStepSizeBar: Double,
    var avgError: Double,
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
    val avgErrorMultiplier =
      1.0 / (iteration.toDouble + acceptanceProbUpdateDenom)
    val stepSizeMultiplier = Math.pow(iteration.toDouble, -decayRate)

    avgError = (
      (1.0 - avgErrorMultiplier) * avgError
        + (avgErrorMultiplier * (delta - newAcceptanceProb))
    )

    logStepSize = (
      shrinkageTarget
        - (avgError * Math.sqrt(iteration.toDouble) / stepSizeUpdateDenom)
    )

    logStepSizeBar = (stepSizeMultiplier * logStepSize
      + (1.0 - stepSizeMultiplier) * logStepSizeBar)
  }
}

private object DualAvg {
  def apply(delta: Double, stepSize: Double): DualAvg =
    new DualAvg(
      delta = delta,
      logStepSize = Math.log(stepSize),
      logStepSizeBar = 0.0,
      avgError = 0.0,
      iteration = 0,
      shrinkageTarget = Math.log(10 * stepSize)
    )

  def findStepSize(delta: Double, stepSize0: Double, iterations: Int)(
      fn: Double => Double): Double = {
    if (stepSize0 == 0.0)
      0.0
    else {
      val dualAvg = DualAvg(delta, stepSize0)
      var i = 0
      while (i < iterations) {
        val logAcceptanceProb = fn(dualAvg.stepSize)
        dualAvg.update(logAcceptanceProb)
        i += 1

        FINER
          .atMostEvery(1, SECONDS)
          .log(
            "iteration %d of %d, stepSize %f, acceptance %f, error %f",
            i,
            iterations,
            dualAvg.stepSize,
            Math.exp(logAcceptanceProb),
            dualAvg.avgError
          )

      }
      dualAvg.finalStepSize
    }
  }

  private def computeExponent(logAcceptanceProb: Double): Double =
    if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def continueTuningStepSize(logAcceptanceProb: Double,
                                     exponent: Double): Boolean =
    exponent * logAcceptanceProb > -exponent * Math.log(2)

  def findReasonableStepSize(lf: LeapFrog, params: Array[Double]): Double = {
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
