package rainier.sampler

case class DualAvg(
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

object DualAvg {
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
}
