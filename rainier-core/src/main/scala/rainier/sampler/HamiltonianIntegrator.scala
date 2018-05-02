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

private case class RealLeapFrogIntegrator(
    variables: Seq[Variable],
    cf: Array[Double] => (Double, Array[Double]))
    extends HamiltonianIntegrator {

  val nVars = variables.size
  val momenta = (1 to nVars).map { _ =>
    new Variable
  }
  val inputs = variables ++ momenta

  private def halfStepPs(inputs: Seq[Variable],
                         realGrad: Seq[Real],
                         stepSize: Double) = {
    val (qs, ps) = (inputs.take(nVars), inputs.drop(nVars))
    val newPs = ps
      .zip(realGrad)
      .map { case (p, grad) => p - (stepSize / 2) * grad }

    Compiler.default.compile(inputs, qs ++ newPs)
  }

  private def fullStepQs(inputs: Seq[Variable], stepSize: Double) = {
    val (qs, ps) = (inputs.take(nVars), inputs.drop(nVars))
    val newQs = qs
      .zip(ps)
      .map { case (q, p) => q + (stepSize * p) }
    Compiler.default.compile(inputs, newQs ++ ps)
  }

  def step(hParams: HParams, stepSize: Double): HParams = {
    val inputArray = hParams.qs ++ hParams.ps
    val initialRealGrad = hParams.gradPotential.map { Real(_) }
    val cf0 = halfStepPs(inputs, initialRealGrad, stepSize)
    val cf1 = fullStepQs(inputs, stepSize)

    val newQsHalfNewPs = (cf0 andThen cf1)(inputArray)
    val newQs = newQsHalfNewPs.take(nVars)
    val (potential, gradPotential) = cf(newQs)

    val updatedRealGrad = gradPotential.map { Real(_) }
    val cf2 = halfStepPs(inputs, updatedRealGrad, stepSize)

    val newQsAndPs = cf2(newQsHalfNewPs)
    hParams.copy(
      potential = potential,
      gradPotential = gradPotential,
      qs = newQsAndPs.take(nVars),
      ps = newQsAndPs.drop(nVars),
    )
  }
}
