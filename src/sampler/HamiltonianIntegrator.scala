package rainier.sampler

import rainier.compute._

/**
  * The Hamiltonian update algorithm is called leapfrog:
  * we make a half-step to update the ps,
  * a full step to update the qs with the half-updated ps,
  * then another half step to update the ps using the new qs.
  */
trait HamiltonianIntegrator {
  def step(hParams: HParams, stepSize: Double): HParams
}

case class LeapFrogIntegrator(negativeDensity: Real,
                              variables: Seq[Variable],
                              gradient: Seq[Real],
                              cf: Compiler.CompiledFunction)
    extends HamiltonianIntegrator {

  private def halfStepPs(hParams: HParams, stepSize: Double): HParams = {
    val newPs = hParams.ps
      .zip(hParams.gradPotential)
      .map { case (p, grad) => p - (stepSize / 2) * grad }

    hParams.copy(ps = newPs)
  }

  private def fullStepQs(hParams: HParams, stepSize: Double): HParams = {
    val newQs = hParams.qs
      .zip(hParams.ps)
      .map { case (q, p) => q + (stepSize * p) }

    val inputs =
      variables
        .zip(newQs)
        .toMap

    val outputs = cf(inputs)

    hParams.copy(
      qs = newQs,
      gradPotential = gradient.map { g =>
        outputs(g)
      },
      potential = outputs(negativeDensity)
    )
  }

  def step(hParams: HParams, stepSize: Double): HParams = {
    val halfNewPs = halfStepPs(hParams, stepSize)
    val newQs = fullStepQs(halfNewPs, stepSize)
    halfStepPs(newQs, stepSize)
  }
}
