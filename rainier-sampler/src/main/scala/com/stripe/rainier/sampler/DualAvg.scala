package com.stripe.rainier.sampler

class DualAvgTuner(delta: Double) extends StepSizeTuner {
  var da: DualAvg = _

  def initialize(params: Array[Double], lf: LeapFrog): Double = {
    val stepSize0 = findReasonableStepSize(params, lf, IdentityMassMatrix)
    da = DualAvg(delta, stepSize0)
    stepSize0
  }

  def update(logAcceptanceProb: Double): Double = {
    da.update(logAcceptanceProb)
    da.stepSize
  }

  def reset(): Double = {
    val ss = stepSize
    da = DualAvg(delta, ss)
    ss
  }

  def stepSize: Double = {
    da.finalStepSize
  }

  private def findReasonableStepSize(params: Array[Double],
                                     lf: LeapFrog,
                                     mass: MassMatrix): Double = {
    var stepSize = 1.0
    var logAcceptanceProb =
      lf.tryStepping(params, stepSize, mass)
    val exponent = if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }
    val doubleOrHalf = Math.pow(2, exponent)
    while (stepSize != 0.0 && (exponent * logAcceptanceProb > -exponent * Math
             .log(2))) {
      stepSize *= doubleOrHalf
      logAcceptanceProb = lf.tryStepping(params, stepSize, mass)
    }
    stepSize
  }
}

final class DualAvg(
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
}
