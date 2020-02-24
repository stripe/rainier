package com.stripe.rainier.sampler

final private class LeapFrog(density: DensityFunction) {
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
  private val vBuf = new Array[Double](nVars)
  private val vBuf2 = new Array[Double](nVars)

  // instance of parameters used in longestStepUntilUTurn
  private val isUturnBuf = new Array[Double](inputOutputSize)

  def newQs(stepSize: Double, metric: Metric): Unit = {
    velocity(pqBuf, vBuf, metric)
    var i = 0
    while (i < nVars) {
      pqBuf(i + nVars) += (stepSize * vBuf(i))
      i += 1
    }
  }

  def halfPsNewQs(stepSize: Double, metric: Metric): Unit = {
    fullPs(stepSize / 2.0)
    newQs(stepSize, metric)
  }

  def initialHalfThenFullStep(stepSize: Double, metric: Metric): Unit = {
    halfPsNewQs(stepSize, metric)
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

  def fullPsNewQs(stepSize: Double, metric: Metric): Unit = {
    fullPs(stepSize)
    newQs(stepSize, metric)
  }

  def twoFullSteps(stepSize: Double, metric: Metric): Unit = {
    fullPsNewQs(stepSize: Double, metric)
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
  private def steps(l: Int, stepSize: Double, metric: Metric): Unit = {
    initialHalfThenFullStep(stepSize, metric)
    var i = 1
    while (i < l) {
      twoFullSteps(stepSize, metric)
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
                                    stepSize: Double,
                                    metric: Metric): Int = {
    var l = 0
    while (!isUTurn(params)) {
      l += 1
      steps(1, stepSize, metric)
      if (l == l0)
        copy(pqBuf, isUturnBuf)
    }
    if (l < l0) {
      steps(l0 - l, stepSize, metric)
    } else {
      copy(isUturnBuf, pqBuf)
    }

    l
  }

  def longestBatchStep(params: Array[Double],
                       l0: Int,
                       stepSize: Double,
                       metric: Metric)(implicit rng: RNG): (Double, Int) = {

    initializePs(params)
    copy(params, pqBuf)
    val l = longestStepUntilUTurn(params, l0, stepSize, metric)
    val u = rng.standardUniform
    val a = logAcceptanceProb(params, metric)
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
  def tryStepping(params: Array[Double],
                  stepSize: Double,
                  metric: Metric): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize, metric)
    finalHalfStep(stepSize)
    val p = logAcceptanceProb(params, metric)
    p
  }

  //attempt to take N steps
  //this will always clobber the stepSize and ps in params,
  //but will only update the qs if the move is accepted
  def step(params: Array[Double], n: Int, stepSize: Double, metric: Metric)(
      implicit rng: RNG): Double = {
    initializePs(params)
    copy(params, pqBuf)
    steps(n, stepSize, metric)
    val p = logAcceptanceProb(params, metric)
    if (p > Math.log(rng.standardUniform)) {
      copy(pqBuf, params)
    }
    p
  }

  // extract q
  def variables(array: Array[Double], out: Array[Double]): Unit = {
    var i = 0
    while (i < nVars) {
      out(i) = array(i + nVars)
      i += 1
    }
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

  private def logAcceptanceProb(from: Array[Double], metric: Metric): Double = {
    val toPotential = pqBuf(potentialIndex)
    velocity(pqBuf, vBuf, metric)
    val toKinetic = dot(vBuf, pqBuf) / 2.0

    val fromPotential = from(potentialIndex)
    velocity(from, vBuf2, metric)
    val fromKinetic = dot(vBuf2, from) / 2.0

    val deltaH = toKinetic + toPotential - fromKinetic - fromPotential
    if (deltaH.isNaN) {
      Math.log(0.0)
    } else {
      val lap = (-deltaH).min(0.0)
      lap
    }
  }

  private def velocity(in: Array[Double], out: Array[Double], m: Metric): Unit =
    m match {
      case StandardMetric =>
        System.arraycopy(in, 0, out, 0, out.size)
      case DiagonalMetric(elements) =>
        var i = 0
        while (i < out.size) {
          out(i) = in(i) * elements(i)
          i += 1
        }
      case EuclideanMetric(elements) =>
        squareMultiply(elements, in, out)
    }

  private def squareMultiply(matrix: Array[Double],
                             vector: Array[Double],
                             out: Array[Double]): Unit = {
    val n = out.size
    var i = 0
    while (i < n) {
      var y = 0.0
      var j = 0
      while (j < n) {
        y += vector(i) * matrix((i * n) + j)
        j += 1
      }
      out(i) = y
      i += 1
    }
  }

  private def dot(x: Array[Double], y: Array[Double]): Double = {
    var k = 0.0
    var i = 0
    val n = x.size
    while (i < n) {
      k += (x(i) * y(i))
      i += 1
    }
    k
  }

  private def initializePs(array: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
  }
}
