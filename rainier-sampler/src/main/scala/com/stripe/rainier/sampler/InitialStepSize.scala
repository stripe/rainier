package com.stripe.rainier.sampler

object InitialStepSize extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    state.startPhase("Initialize step size", 0)
    var logAcceptanceProb = state.tryStepping()
    val exponent = computeExponent(logAcceptanceProb)
    val doubleOrHalf = Math.pow(2, exponent)
    while (continueTuningStepSize(state.stepSize, logAcceptanceProb, exponent)) {
      state.updateStepSize(state.stepSize * doubleOrHalf)
      logAcceptanceProb = state.tryStepping()
    }
  }

  private def computeExponent(logAcceptanceProb: Double): Double =
    if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }

  private def continueTuningStepSize(stepSize: Double,
                                     logAcceptanceProb: Double,
                                     exponent: Double): Boolean =
    stepSize != 0.0 && (exponent * logAcceptanceProb > -exponent * Math.log(2))
}
