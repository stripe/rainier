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

private case class LeapFrogVars[+A](potential: A,
                                    qs: Seq[A],
                                    ps: Seq[A],
                                    grad: Seq[A]) {
  val toSeq: Seq[A] = potential +: (qs ++ ps ++ grad)
}

private object LeapFrogVars {
  def apply[A](s: Seq[A]): LeapFrogVars[A] = {
    require((s.size - 1) / 3 == 0, "there must be 3n + 1 vars!")
    val nVars = (s.size - 1 / 3).toInt
    LeapFrogVars(
      s.head,
      s.slice(1, nVars + 1),
      s.slice(nVars + 1, 2 * nVars + 1),
      s.slice(2 * nVars + 1, 3 * nVars + 1)
    )
  }
}

private case class RealLeapFrogIntegrator(variables: Seq[Variable],
                                          density: Real)
    extends HamiltonianIntegrator {

  val nVars = variables.size
  val potential = new Variable
  val momenta = (1 to nVars).map { _ =>
    new Variable
  }
  val grad = (1 to nVars).map { _ =>
    new Variable
  }
  val inputs = LeapFrogVars(potential, variables, momenta, grad)

  private def halfStepPs(stepSize: Double)(inputs: LeapFrogVars[Real]) = {
    val newPs = inputs.ps
      .zip(inputs.grad)
      .map { case (p, grad) => p - (stepSize / 2) * grad }

    inputs.copy(ps = newPs)
  }

  private def fullStepQs(stepSize: Double)(inputs: LeapFrogVars[Real]) = {
    val newQs = inputs.qs
      .zip(inputs.ps)
      .map { case (q, p) => q + (stepSize * p) }
    inputs.copy(qs = newQs)
  }

  private def updateGrad(inputs: LeapFrogVars[Real]): LeapFrogVars[Real] =
    ??? //{
//    val newGrad = Gradient.derive(inputs.qs, density * -1)
//    inputs.copy(grad = newGrad)
//  }

  private def cfOutput(stepSize: Double): Seq[Real] = {
    (halfStepPs(stepSize) _
      andThen fullStepQs(stepSize) _
      andThen updateGrad _
      andThen halfStepPs(stepSize) _)(inputs).toSeq
  }

  private def leapFrogCF(stepSize: Double) =
    Compiler.default.compile(inputs.toSeq, cfOutput(stepSize))

  def step(hParams: HParams, stepSize: Double): HParams = {
    val array = LeapFrogVars(potential = hParams.potential,
                             qs = hParams.qs,
                             ps = hParams.ps,
                             grad = hParams.gradPotential).toSeq.toArray
    val outArray = leapFrogCF(stepSize)(array)
    val outVars = LeapFrogVars(outArray.toSeq)
    hParams.copy(
      potential = outVars.potential,
      qs = outVars.qs.toArray,
      ps = outVars.ps.toArray,
      gradPotential = outVars.grad.toArray
    )
  }
}
