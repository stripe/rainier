package com.stripe.rainier.sampler

private[sampler] case class LeapFrog(density: DensityFunction) {
  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
   */
  private val nVars = density.nVars
  private val potentialIndex = nVars * 2
  private val inputOutputSize = potentialIndex + 1
  private val pqBuf = new Array[Double](inputOutputSize)
  private val qBuf = new Array[Double](nVars)

  // instance of parameters used in longestStepUntilUTurn
  private val isUturnBuf = new Array[Double](inputOutputSize)

  def newQs(stepSize: Double): Unit = {
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      pqBuf(i) += (stepSize * pqBuf(i - nVars))
      i += 1
    }
  }

  def halfPsNewQs(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
    newQs(stepSize)
  }

  def initialHalfThenFullStep(stepSize: Double): Unit = {
    halfPsNewQs(stepSize)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  def fullPs(stepSize: Double): Unit = {
    copyQsAndUpdateDensity()
    var i = 0
    val j = nVars
    while (i < j) {
      pqBuf(i) += stepSize * density.gradient(i)
      i += 1
    }
  }

  def fullPsNewQs(stepSize: Double): Unit = {
    fullPs(stepSize)
    newQs(stepSize)
  }

  def twoFullSteps(stepSize: Double): Unit = {
    fullPsNewQs(stepSize: Double)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  def finalHalfStep(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  /**
    * Perform l leapfrog steps updating pqBuf
    * @param l the total number of leapfrog steps to perform
    * @param stepSize the current step size
    */
  private def steps(l: Int, stepSize: Double): Unit = {
    initialHalfThenFullStep(stepSize)
    var i = 1
    while (i < l) {
      twoFullSteps(stepSize)
      i += 1
    }
    finalHalfStep(stepSize)
  }

  /**
    * Determine if a u-turn has been performed by checking the initial
    * parameters against pqBuf
    */
  private def isUTurn(params: Array[Double]): Boolean = {

    var out = 0.0
    var i = 0
    while (i < nVars) {
      out += (pqBuf(i + nVars) - params(i + nVars)) * pqBuf(i)
      i += 1
    }

    if (out.isNaN)
      true
    else
      out < 0
  }

  /**
    * Advance l0 steps and return the value
    * of the longest-step size until a u-turn
    */
  private def longestStepUntilUTurn(params: Array[Double],
                                    l0: Int,
                                    stepSize: Double): Int = {
    var l = 0
    while (!isUTurn(params)) {
      l += 1
      steps(1, stepSize)
      if (l == l0)
        copy(pqBuf, isUturnBuf)
    }
    if (l < l0) {
      steps(l0 - l, stepSize)
    } else {
      copy(isUturnBuf, pqBuf)
    }

    l
  }

  /**
    * Perform a single step of the longest batch step algorithm
    * @param params the current value of the parameters
    * @param l0 the initial number of steps
    * @param stepSize the current value of the leapfrog step size
    */
  def longestBatchStep(params: Array[Double], l0: Int, stepSize: Double)(
      implicit rng: RNG): (Double, Int) = {

    initializePs(params)
    copy(params, pqBuf)
    val l = longestStepUntilUTurn(params, l0, stepSize)
    val u = rng.standardUniform
    val a = logAcceptanceProb(params, pqBuf)
    if (math.log(u) < a)
      copy(pqBuf, params)
    (a, l)
  }

  private def copy(sourceArray: Array[Double],
                   targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  private def copyQsAndUpdateDensity(): Unit = {
    System.arraycopy(pqBuf, nVars, qBuf, 0, nVars)
    density.update(qBuf)
  }
  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double], stepSize: Double): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize)
    finalHalfStep(stepSize)
    val p = logAcceptanceProb(params, pqBuf)
    p
  }

  //attempt to take N steps
  //this will always clobber the stepSize and ps in params,
  //but will only update the qs if the move is accepted
  def step(params: Array[Double], n: Int, stepSize: Double)(
      implicit rng: RNG): Double = {
    initializePs(params)
    copy(params, pqBuf)
    steps(n, stepSize)
    val p = logAcceptanceProb(params, pqBuf)
    if (p > Math.log(rng.standardUniform)) {
      copy(pqBuf, params)
    }
    p
  }

  // extract q
  def variables(array: Array[Double]): Array[Double] = {
    val newArray = new Array[Double](nVars)
    var i = 0
    while (i < nVars) {
      newArray(i) = array(i + nVars)
      i += 1
    }
    newArray
  }

  //we want the invariant that a params array always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential.
  def initialize(implicit rng: RNG): Array[Double] = {
    java.util.Arrays.fill(pqBuf, 0.0)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      pqBuf(i) = rng.standardNormal
      i += 1
    }
    val array = new Array[Double](inputOutputSize)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
    copy(pqBuf, array)
    initializePs(array)
    array
  }

  /**
    * This is the dot product (ps^T ps).
    * The fancier variations of HMC involve changing this kinetic term
    * to either take the dot product with respect to a non-identity matrix (ps^T M ps)
    * (a non-standard Euclidean metric) or a matrix that depends on the qs
    * (ps^T M(qs) ps) (a Riemannian metric)
    */
  private def kinetic(array: Array[Double]): Double = {
    var k = 0.0
    var i = 0
    while (i < nVars) {
      val p = array(i)
      k += (p * p)
      i += 1
    }
    k / 2.0
  }

  private def logAcceptanceProb(from: Array[Double],
                                to: Array[Double]): Double = {
    val deltaH = kinetic(to) + to(potentialIndex) - kinetic(from) - from(
      potentialIndex)
    if (deltaH.isNaN) {
      Math.log(0.0)
    } else {
      val lap = (-deltaH).min(0.0)
      lap
    }
  }

  private def initializePs(array: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
  }
}
