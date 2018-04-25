package rainier.sampler

import rainier.compute._
import rainier.compute.compiler._

private case class HamiltonianChain(
    accepted: Boolean,
    acceptanceProb: Double,
    hParams: HParams,
    cf: Array[Double] => (Double, Array[Double]))(implicit rng: RNG) {

  // Take a single leapfrog step without re-initializing momenta
  // for use in tuning the step size
  def stepOnce(stepSize: Double): HamiltonianChain = {
    val newParams = integrator.step(hParams, stepSize)
    copy(hParams = newParams)
  }

  def nextHMC(stepSize: Double, nSteps: Int): HamiltonianChain = {
    val initialParams =
      HParams(hParams.qs, hParams.gradPotential, hParams.potential)
    val finalParams = (1 to nSteps)
      .foldLeft(initialParams) {
        case (params, _) => integrator.step(params, stepSize)
      }
    val deltaH =
      finalParams.hamiltonian - initialParams.hamiltonian

    //we accept the proposal with probability min{1, exp(-deltaH)}
    val newAcceptanceProb = Math.exp(-deltaH).min(1.0)
    val (newParams, newAccepted) = {
      if (rng.standardUniform < newAcceptanceProb)
        (finalParams, true)
      else
        (initialParams, false)
    }
    copy(
      hParams = newParams,
      accepted = newAccepted,
      acceptanceProb = newAcceptanceProb
    )
  }

  def nextNUTS(stepSize: Double, maxDepth: Int): HamiltonianChain = {
    val initialParams =
      HParams(hParams.qs, hParams.gradPotential, hParams.potential)
    val newParams =
      NUTSStep(initialParams, stepSize, integrator, maxDepth).run
    val newAccepted = (hParams.qs != newParams.qs)
    copy(
      hParams = newParams,
      accepted = newAccepted
    )
  }

  private def integrator = LeapFrogIntegrator(cf)
}

private object HamiltonianChain {

  def apply(variables: Seq[Variable], density: Real)(
      implicit rng: RNG): HamiltonianChain = {
    val negativeDensity = density * -1
    val cf = Compiler.compileGradient(variables, negativeDensity)
    val hParams = initialize(variables.size, cf)
    HamiltonianChain(true, 1.0, hParams, cf)
  }

  def initialize(nVars: Int, cf: Array[Double] => (Double, Array[Double]))(
      implicit rng: RNG): HParams = {
    val qs = 1
      .to(nVars)
      .map { v =>
        rng.standardNormal
      }
      .toArray

    val (potential, gradPotential) = cf(qs)

    HParams(qs, gradPotential, potential)
  }
}

private case class HParams(
    qs: Array[Double],
    ps: Array[Double],
    gradPotential: Array[Double],
    potential: Double
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
}

private object HParams {

  def apply(qs: Array[Double], gradPotential: Array[Double], potential: Double)(
      implicit rng: RNG): HParams = {
    val ps = qs.map { _ =>
      rng.standardNormal
    }

    HParams(qs, ps, gradPotential, potential)
  }
}
