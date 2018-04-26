package rainier.sampler

import rainier.compute._

/**
  * The Hamiltonian update algorithm is called leapfrog:
  * we make a half-step to update the ps,
  * a full step to update the qs with the half-updated ps,
  * then another half step to update the ps using the new qs.
  */
private trait HamiltonianIntegrator {
  def step(hParams: HParams, stepSize: Double): HParams
}

private case class LeapFrogIntegrator(
    cf: Array[Double] => (Double, Array[Double]))
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

    val (potential, gradPotential) = cf(newQs)

    hParams.copy(
      qs = newQs,
      gradPotential = gradPotential,
      potential = potential
    )
  }

  def step(hParams: HParams, stepSize: Double): HParams = {
    val halfNewPs = halfStepPs(hParams, stepSize)
    val newQs = fullStepQs(halfNewPs, stepSize)
    halfStepPs(newQs, stepSize)
  }
}
