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

class HParams(
    qs: Array[Double],
    ps: Array[Double],
    gradPotential: Array[Double],
    potential: Double,
    stepSize: Double
) {

  /**
    * This is the dot product (ps^T ps).
    * The fancier variations of HMC involve changing this kinetic term
    * to either take the dot product with respect to a non-identity matrix (ps^T M ps)
    * (a non-standard Euclidean metric) or a matrix that depends on the qs
    * (ps^T M(qs) ps) (a Riemannian metric)
    */
  private val kinetic = ps.map { p =>
    p * p
  }.sum / 2

  val hamiltonian = kinetic + potential

  def logAcceptanceProb(nextParams: HParams): Double = {
    val deltaH = nextParams.hamiltonian - this.hamiltonian
    if (deltaH.isNaN) { Math.log(0.0) } else { (-deltaH).min(0.0) }
  }

  def nextIteration(newStepSize: Double)(implicit rng: RNG): HParams = {
    val newPs = qs.map { _ =>
      rng.standardNormal
    }
    new HParams(qs, newPs, gradPotential, potential, newStepSize)
  }

  def stepSize(newStepSize: Double): HParams =
    new HParams(qs, ps, gradPotential, potential, newStepSize)

  def variables = qs

  def toArray: Array[Double] =
    stepSize +: potential +: (qs ++ ps ++ gradPotential)
}
