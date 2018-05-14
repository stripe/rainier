package rainier.sampler

import rainier.compute._

private case class HamiltonianChain(accepted: Boolean,
                                    acceptanceProb: Double,
                                    hParams: HParams,
                                    lf: LeapFrog) {

  // Take a single leapfrog step without re-initializing momenta
  // for use in tuning the step size
  def stepOnce(stepSize: Double): HamiltonianChain = {
    val newParams = lf.steps(1, hParams.stepSize(stepSize))
    copy(hParams = newParams)
  }

  def nextHMC(stepSize: Double, nSteps: Int)(
      implicit rng: RNG): HamiltonianChain = {
    val initialParams = hParams.nextIteration(stepSize)
    val finalParams = lf.steps(nSteps, initialParams)
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
    val lf = LeapFrog(variables.toList, density)
    val hParams = lf.initialize
    HamiltonianChain(true, 1.0, hParams, lf)
  }
}

/*
array(0..(n-1)) == ps
array(n..(n*2-1)) == qs
array(n*2) == potential
array(n*2+1) == stepSize
 */
case class HParams(array: Array[Double]) {
  val n = (array.size - 2) / 2
  def potentialIndex = n * 2
  def stepSizeIndex = potentialIndex + 1

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

  private def potential = array(potentialIndex)

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
    newArray(stepSizeIndex) = newStepSize
    HParams(newArray)
  }

  def stepSize(newStepSize: Double): HParams = {
    val newArray = array.clone
    newArray(stepSizeIndex) = newStepSize
    HParams(newArray)
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
}

private class LeapFrog(
    nVars: Int,
    initialHalfThenFullStep: Array[Double] => Array[Double],
    twoFullSteps: Array[Double] => Array[Double],
    finalHalfStep: Array[Double] => Array[Double]
) {
  def steps(n: Int, input: HParams): HParams = {
    var result = initialHalfThenFullStep(input.array)
    var i = 1
    while (i < n) {
      result = twoFullSteps(result)
      i += 1
    }
    HParams(finalHalfStep(result))
  }

  //we want the invariant that an HParams always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential. We can do that (slightly wastefully)
  //by using initialHalfThenFullStep with a stepSize of 0.0
  def initialize(implicit rng: RNG): HParams = {
    val array = new Array[Double](nVars * 2 + 2)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      array(i) = rng.standardNormal
      i += 1
    }
    HParams(initialHalfThenFullStep(array)).nextIteration(0.0)
  }
}

private object LeapFrog {
  def apply(qs: List[Variable], density: Real): LeapFrog = {
    val ps = qs.map { _ =>
      new Variable
    }
    val potential = new Variable
    val stepSize = new Variable
    val inputs: List[Variable] = (ps ++ qs) :+ potential :+ stepSize

    val newPotential = density * -1
    val grad = newPotential.gradient

    val halfPs =
      ps.zip(newPotential.gradient).map {
        case (p, g) => p - (stepSize / 2) * g
      }
    val halfPsNewQs =
      qs.zip(halfPs).map { case (q, p) => q + (stepSize * p) }

    val initialHalfThenFullStep =
      (halfPs ++ halfPsNewQs) :+ newPotential :+ stepSize

    val fullPs =
      ps.zip(grad).map { case (p, g) => p - stepSize * g }

    val fullPsNewQs =
      qs.zip(fullPs).map { case (q, p) => q + (stepSize * p) }

    val twoFullSteps =
      (fullPs ++ fullPsNewQs) :+ newPotential :+ stepSize

    val finalHalfStep =
      (halfPs ++ qs) :+ newPotential :+ stepSize

    new LeapFrog(
      qs.size,
      Compiler.default.compile(inputs, initialHalfThenFullStep),
      Compiler.default.compile(inputs, twoFullSteps),
      Compiler.default.compile(inputs, finalHalfStep)
    )
  }
}
