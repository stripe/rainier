package rainier.sampler

import rainier.compute._

private case class HamiltonianChain(accepted: Boolean,
                                    acceptanceProb: Double,
                                    hParams: HParams) {

  // Take a single leapfrog step without re-initializing momenta
  // for use in tuning the step size
  def stepOnce(stepSize: Double): HamiltonianChain = {
    val newParams = hParams.stepSize(stepSize).step
    copy(hParams = newParams)
  }

  def nextHMC(stepSize: Double, nSteps: Int)(
      implicit rng: RNG): HamiltonianChain = {
    val initialParams = hParams.nextIteration(stepSize)
    val finalParams = (1 to nSteps)
      .foldLeft(initialParams) {
        case (params, _) => params.step
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
    val hParams = HParams.initialize(variables.toList, density)
    HamiltonianChain(true, 1.0, hParams)
  }
}

/*
array(0..(n-1)) == ps
array(n..(n*2-1)) == qs
array(n*2..(n*3-1)) == gradPotential
array(n*3) == potential
array(n*3+1) == stepSize
 */
class HParams(array: Array[Double], lf: Array[Double] => Array[Double]) {
  val n = (array.size - 2) / 3

  /**
    * This is the dot product (ps^T ps).
    * The fancier variations of HMC involve changing this kinetic term
    * to either take the dot product with respect to a non-identity matrix (ps^T M ps)
    * (a non-standard Euclidean metric) or a matrix that depends on the qs
    * (ps^T M(qs) ps) (a Riemannian metric)
    */
  private def kinetic = {
    var k = 0.0
    var i = 0
    while (i < n) {
      val p = array(i)
      k += (p * p)
      i += 1
    }
    k / 2.0
  }

  private def potential = array(n * 3)

  def logAcceptanceProb(nextParams: HParams): Double = {
    val deltaH = nextParams.kinetic + nextParams.potential - kinetic - potential
    if (deltaH.isNaN) { Math.log(0.0) } else { (-deltaH).min(0.0) }
  }

  def nextIteration(newStepSize: Double)(implicit rng: RNG): HParams = {
    val newArray = array.clone
    var i = 0
    while (i < n) {
      newArray(i) = rng.standardNormal
      i += 1
    }
    newArray(n * 3 + 1) = newStepSize
    new HParams(newArray, lf)
  }

  def stepSize(newStepSize: Double): HParams = {
    val newArray = array.clone
    newArray(n * 3 + 1) = newStepSize
    new HParams(newArray, lf)
  }

  def variables: Array[Double] = {
    val newArray = new Array[Double](n)
    var i = 0
    while (i < n) {
      newArray(i) = array(i + n)
      i += 1
    }
    newArray
  }

  def step: HParams =
    new HParams(lf(array), lf)
}

object HParams {
  def initialize(qs: List[Variable], density: Real)(
      implicit rng: RNG): HParams = {
    val array = new Array[Double](qs.size * 3 + 2)
    var i = qs.size
    val j = qs.size * 2
    while (i < j) {
      array(i) = rng.standardNormal
      i += 1
    }

    val lf = compileLeapFrog(qs, density)
    new HParams(lf(array), lf)
  }

  private def compileLeapFrog(qs: List[Variable],
                              density: Real): Array[Double] => Array[Double] = {
    val ps = qs.map { _ =>
      new Variable
    }
    val grad = qs.map { _ =>
      new Variable
    }
    val potential = new Variable
    val stepSize = new Variable
    val inputs: List[Variable] = (ps ++ qs ++ grad) :+ potential :+ stepSize

    val newPs =
      ps.zip(grad).map { case (p, g) => p - (stepSize / 2) * g }

    val newQs =
      qs.zip(newPs).map { case (q, p) => q + (stepSize * p) }

    val halfStepThenFullStepOutputs: List[Real] =
      (newPs ++ newQs ++ grad) :+ potential :+ stepSize

    val newPotential = density * -1
    val newGrad = newPotential.gradient

    val newNewPs =
      ps.zip(newGrad).map { case (p, g) => p - (stepSize / 2) * g }

    val newGradThenHalfStepOutputs: List[Real] =
      (newNewPs ++ qs ++ newGrad) :+ newPotential :+ stepSize

    val cf1 = Compiler.default.compile(inputs, halfStepThenFullStepOutputs)
    val cf2 = Compiler.default.compile(inputs, newGradThenHalfStepOutputs)
    cf1.andThen(cf2)
  }
}
