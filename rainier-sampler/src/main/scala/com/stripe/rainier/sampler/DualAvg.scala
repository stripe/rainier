package com.stripe.rainier.sampler

case class DualAvgStepSize(iterations: Int, delta: Double) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    val dualAvg = DualAvg(delta, state.stepSize)
    var i = 0
    state.startPhase("Finding step size with dual averaging", iterations)
    while (i < iterations) {
      state.updateStepSize(dualAvg.stepSize)
      val logAcceptanceProb = state.step()
      dualAvg.update(logAcceptanceProb)
      i += 1
    }
    state.updateStepSize(dualAvg.finalStepSize)
  }
}

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
}
