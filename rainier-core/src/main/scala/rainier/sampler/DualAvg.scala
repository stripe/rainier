package rainier.sampler

case class DualAvg(
    delta: Double,
    lambda: Double,
    logEpsilon: Double,
    logEpsilonBar: Double,
    acceptanceProb: Double,
    hBar: Double,
    iteration: Int,
    mu: Double,
    gamma: Double = 0.05,
    t: Int = 10,
    kappa: Double = 0.75
) {
  val stepSize = Math.exp(logEpsilon)
  val finalStepSize = Math.exp(logEpsilonBar)
  val nSteps = (lambda / Math.exp(logEpsilon)).toInt.max(1)

  def update(newAcceptanceProb: Double): DualAvg = {
    val newIteration = iteration + 1
    val hBarMultiplier = 1.0 / (newIteration + t)
    val epsilonMultiplier = Math.pow(newIteration, -kappa)
    val newHBar = (
      (1.0 - hBarMultiplier) * hBar
        + (hBarMultiplier * (delta - newAcceptanceProb))
    )

    val newLogEpsilon =
      mu - (newHBar * Math.sqrt(newIteration) / gamma)

    val newLogEpsilonBar = (epsilonMultiplier * newLogEpsilon
      + (1.0 - epsilonMultiplier) * logEpsilonBar)
    println(s"m: $newIteration")
    println(s"epsilon_m: $newLogEpsilon")
    println(
      s"epsilonBar: ${Math.exp(logEpsilonBar)}, ${Math.exp(newLogEpsilonBar)}")
    println(s"acceptance prob: ${acceptanceProb}, ${newAcceptanceProb}")
    copy(iteration = newIteration,
         acceptanceProb = newAcceptanceProb,
         hBar = newHBar,
         logEpsilon = newLogEpsilon,
         logEpsilonBar = newLogEpsilonBar)
  }
}

object DualAvg {
  def apply(delta: Double, lambda: Double, epsilon: Double): DualAvg =
    DualAvg(
      delta = delta,
      lambda = lambda,
      logEpsilon = Math.log(epsilon),
      logEpsilonBar = 0.0,
      acceptanceProb = 1.0,
      hBar = 0.0,
      iteration = 0,
      mu = Math.log(10 * epsilon)
    )
}
