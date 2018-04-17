package rainier.sampler

import rainier.compute._

case class HamiltonianChain(accepted: Boolean,
                            hParams: HParams,
                            negativeDensity: Real,
                            variables: Seq[Variable],
                            gradient: Seq[Real],
                            cf: Compiler.CompiledFunction)(implicit rng: RNG) {

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
    val (newParams, newAccepted) = {
      if (deltaH < 0 || rng.standardUniform < Math.exp(-deltaH))
        (finalParams, true)
      else
        (initialParams, false)
    }
    copy(hParams = newParams, accepted = newAccepted)
  }

  def nextNUTS(stepSize: Double, maxDepth: Int = 6): HamiltonianChain = {
    val initialParams =
      HParams(hParams.qs, hParams.gradPotential, hParams.potential)
    val newParams =
      NUTS(initialParams, stepSize, integrator, maxDepth).run
    val newAccepted = (hParams.qs != newParams.qs)
    copy(
      hParams = newParams,
      accepted = newAccepted
    )
  }

  private def integrator =
    LeapFrogIntegrator(negativeDensity, variables, gradient, cf)
}

object HamiltonianChain {

  def apply(density: Real)(implicit rng: RNG): HamiltonianChain = {
    val variables = Real.variables(density).toList
    val negativeDensity = density * -1
    val gradient = Gradient.derive(variables, negativeDensity).toList
    val cf = Compiler(negativeDensity :: gradient)
    val hParams = initialize(negativeDensity, variables, gradient, cf)
    HamiltonianChain(true, hParams, negativeDensity, variables, gradient, cf)
  }

  def initialize(negativeDensity: Real,
                 variables: Seq[Variable],
                 gradient: Seq[Real],
                 cf: Compiler.CompiledFunction)(implicit rng: RNG): HParams = {
    val qs = variables.map { v =>
      rng.standardNormal
    }
    val inputs =
      variables
        .zip(qs)
        .toMap

    val outputs = cf(inputs)

    val gradPotential = gradient.map { g =>
      outputs(g)
    }
    val potential = outputs(negativeDensity)
    HParams(qs, gradPotential, potential)
  }
}

case class HParams(
    qs: Seq[Double],
    ps: Seq[Double],
    gradPotential: Seq[Double],
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

object HParams {

  def apply(qs: Seq[Double], gradPotential: Seq[Double], potential: Double)(
      implicit rng: RNG): HParams = {
    val ps = (1 to qs.size).map { _ =>
      rng.standardNormal
    }
    HParams(qs, ps, gradPotential, potential)
  }
}
