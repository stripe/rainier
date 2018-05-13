package rainier.sampler

import rainier.compute._

private case class HamiltonianChain(
    accepted: Boolean,
    acceptanceProb: Double,
    hParams: HParams,
    integrator: HamiltonianIntegrator)(implicit rng: RNG) {

  // Take a single leapfrog step without re-initializing momenta
  // for use in tuning the step size
  def stepOnce(stepSize: Double): HamiltonianChain = {
    val newParams = integrator.step(hParams.stepSize(stepSize))
    copy(hParams = newParams)
  }

  def nextHMC(stepSize: Double, nSteps: Int): HamiltonianChain = {
    val initialParams = hParams.nextIteration(stepSize)
    val finalParams = (1 to nSteps)
      .foldLeft(initialParams) {
        case (params, _) => integrator.step(params)
      }
    val logAcceptanceProb = initialParams.logAcceptanceProb(finalParams)
    val (newParams, newAccepted) = {
      if (Math.log(rng.standardUniform) < logAcceptanceProb)
        (finalParams, true)
      else
        (initialParams, false)
    }
    copy(
      hParams = newParams,
      accepted = newAccepted,
      acceptanceProb = Math.exp(logAcceptanceProb)
    )
  }

  def logAcceptanceProb(nextChain: HamiltonianChain): Double =
    this.hParams.logAcceptanceProb(nextChain.hParams)

  def variables = hParams.variables
}

private object HamiltonianChain {

  def apply(variables: Seq[Variable], density: Real)(
      implicit rng: RNG): HamiltonianChain = {
    val integrator = LeapFrogIntegrator(variables, density)
    val hParams = integrator.initialize
    HamiltonianChain(true, 1.0, hParams, integrator)
  }
}
