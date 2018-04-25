package rainier.sampler

case class DualAvg(
    delta: Double,
    lambda: Double,
    logStepSize: Double,
    logStepSizeBar: Double,
    acceptanceProb: Double,
    hBar: Double,
    iteration: Int,
    mu: Double,
    gamma: Double = 0.05,
    t: Int = 10,
    kappa: Double = 0.75
) {
  val stepSize = Math.exp(logStepSize)
  val finalStepSize = Math.exp(logStepSizeBar)
  val nSteps = (lambda / Math.exp(logStepSize)).toInt.max(1)

  def update(newAcceptanceProb: Double): DualAvg = {
    val newIteration = iteration + 1
    val hBarMultiplier = 1.0 / (newIteration + t)
    val stepSizeMultiplier = Math.pow(newIteration, -kappa)
    val newHBar = (
      (1.0 - hBarMultiplier) * hBar
        + (hBarMultiplier * (delta - newAcceptanceProb))
    )

    val newLogStepSize =
      mu - (newHBar * Math.sqrt(newIteration) / gamma)

    val newLogStepSizeBar = (stepSizeMultiplier * newLogStepSize
      + (1.0 - stepSizeMultiplier) * logStepSizeBar)

    copy(iteration = newIteration,
         acceptanceProb = newAcceptanceProb,
         hBar = newHBar,
         logStepSize = newLogStepSize,
         logStepSizeBar = newLogStepSizeBar)
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
      hBar = 0.0,
      iteration = 0,
      mu = Math.log(10 * stepSize)
    )
}
