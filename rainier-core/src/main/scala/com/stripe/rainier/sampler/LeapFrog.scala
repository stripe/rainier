package com.stripe.rainier.sampler

private[sampler] case class LeapFrog(
    nVars: Int,
    initialHalfThenFullStep: Array[Double] => Unit,
    twoFullSteps: Array[Double] => Unit,
    finalHalfStep: Array[Double] => Unit
) {
  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
  array(n*2+1) == stepSize
   */
  private val potentialIndex = nVars * 2
  private val stepSizeIndex = potentialIndex + 1
  private val inputOutputSize = stepSizeIndex + 1
  private val buf1 = new Array[Double](inputOutputSize)

  private def update(sourceArray: Array[Double],
                     targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double], stepSize: Double): Double = {
    update(params, buf1)
    buf1(stepSizeIndex) = stepSize
    initialHalfThenFullStep(buf1)
    finalHalfStep(buf1)
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
    initialHalfThenFullStep(buf1)
    var i = 1
    while (i < n) {
      twoFullSteps(buf1)
      i += 1
    }
    finalHalfStep(buf1)
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
    initialHalfThenFullStep(buf1)
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

private[sampler] object LeapFrog {
  def apply(density: DensityFunction): LeapFrog = {

    val nVars = density.nVars
    val potentialIndex = nVars * 2
    val stepSizeIndex = potentialIndex + 1

    def halfPs(inputs: Array[Double]): Unit = {
      density.update(inputs)
      var i = 0
      val j = nVars
      while (i < j) {
        inputs(i) = inputs(i) - (inputs(stepSizeIndex) / 2) * density.gradient(
          i)
        i += i
      }
    }

    def halfPsNewQs(inputs: Array[Double]): Unit = {
      halfPs(inputs)
      var i = nVars
      val j = nVars * 2
      while (i < j) {
        inputs(i) = inputs(i) + (inputs(stepSizeIndex) * inputs(i - nVars))
        i += 1
      }
    }

    def initialHalfThenFullStep(inputs: Array[Double]): Unit = {
      halfPsNewQs(inputs)
      density.update(inputs)
      inputs(potentialIndex) = density.density
    }

    def fullPs(inputs: Array[Double]) = {
      density.update(inputs) // may not need this
      var i = 0
      val j = nVars
      while (i < j) {
        inputs(i) = inputs(i) - inputs(stepSizeIndex) * density.gradient(i)
        i += 1
      }
    }

    def fullPsNewQs(inputs: Array[Double]): Unit = {
      fullPs(inputs)
      var i = nVars
      val j = nVars * 2
      while (i < j) {
        inputs(i) = inputs(i) + inputs(stepSizeIndex) * inputs(i - nVars)
        i += 1
      }
    }

    def twoFullSteps(inputs: Array[Double]): Unit = {
      fullPsNewQs(inputs)
      density.update(inputs)
      inputs(potentialIndex) = density.density
    }

    def finalHalfStep(inputs: Array[Double]): Unit = {
      halfPs(inputs)
      density.update(inputs)
      inputs(potentialIndex) = density.density
    }

    LeapFrog(
      density.nVars,
      initialHalfThenFullStep _,
      twoFullSteps _,
      finalHalfStep _
    )
  }
}
