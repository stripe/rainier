package com.stripe.rainier.sampler

private[sampler] case class LeapFrog(density: DensityFunction) {
  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
  array(n*2+1) == stepSize
   */
  private val nVars = density.nVars
  private val potentialIndex = nVars * 2
  private val stepSizeIndex = potentialIndex + 1
  private val inputOutputSize = stepSizeIndex + 1
  private val buf1 = new Array[Double](inputOutputSize)

  def halfPs(): Unit = {
    density.update(buf1)
    var i = 0
    val j = nVars
    while (i < j) {
      buf1(i) -= (buf1(stepSizeIndex) / 2) * density.gradient(i) * -1
      i += i
    }
  }

  def halfPsNewQs(): Unit = {
    halfPs()
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      buf1(i) += (buf1(stepSizeIndex) * buf1(i - nVars))
      i += 1
    }
  }

  def initialHalfThenFullStep(): Unit = {
    halfPsNewQs()
    density.update(buf1)
    buf1(potentialIndex) = density.density * -1
  }

  def fullPs(): Unit = {
    density.update(buf1)
    var i = 0
    val j = nVars
    while (i < j) {
      buf1(i) -= buf1(stepSizeIndex) * density.gradient(i) * -1
      i += 1
    }
  }

  def fullPsNewQs(): Unit = {
    fullPs()
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      buf1(i) += buf1(stepSizeIndex) * buf1(i - nVars)
      i += 1
    }
  }

  def twoFullSteps(): Unit = {
    fullPsNewQs()
    density.update(buf1)
    buf1(potentialIndex) = density.density * -1
  }

  def finalHalfStep(): Unit = {
    halfPs()
    density.update(buf1)
    buf1(potentialIndex) = density.density * -1
  }

  private def update(sourceArray: Array[Double],
                     targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double], stepSize: Double): Double = {
    update(params, buf1)
    buf1(stepSizeIndex) = stepSize
    initialHalfThenFullStep()
    finalHalfStep()
    logAcceptanceProb(params, buf1)
  }

  //attempt to take N steps
  //this will always clobber the stepSize and ps in params,
  //but will only update the qs if the move is accepted
  def step(params: Array[Double], n: Int, stepSize: Double)(
      implicit rng: RNG): Double = {
    initializePs(params)
    params(stepSizeIndex) = stepSize
    update(params, buf1)
    initialHalfThenFullStep()
    var i = 1
    while (i < n) {
      twoFullSteps()
      i += 1
    }
    finalHalfStep()
    val p = logAcceptanceProb(params, buf1)
    if (p > Math.log(rng.standardUniform))
      update(buf1, params)
    p
  }

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
  //we need to compute the potential. We can do that (slightly wastefully)
  //by using initialHalfThenFullStep with a stepSize of 0.0
  def initialize(implicit rng: RNG): Array[Double] = {
    java.util.Arrays.fill(buf1, 0.0)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      buf1(i) = rng.standardNormal
      i += 1
    }
    val array = new Array[Double](inputOutputSize)
    initialHalfThenFullStep()
    update(buf1, array)
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
    if (deltaH.isNaN) { Math.log(0.0) } else { (-deltaH).min(0.0) }
  }

  private def initializePs(array: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
  }
}
