package com.stripe.rainier.sampler

final class LeapFrog(density: DensityFunction) {
  val stats = new Stats(100)

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double],
                  stepSize: Double,
                  metric: Metric): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize, metric)
    finalHalfStep(stepSize)
    logAcceptanceProb(params, metric)
  }

  def takeSteps(l: Int, stepSize: Double, metric: Metric): Unit = {
    stats.stepSizes.add(stepSize)
    initialHalfThenFullStep(stepSize, metric)
    var i = 1
    while (i < l) {
      twoFullSteps(stepSize, metric)
      i += 1
    }
    finalHalfStep(stepSize)
  }

  def isUTurn(params: Array[Double]): Boolean = {
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

  def logAcceptanceProb(params: Array[Double], metric: Metric): Double = {
    val toPotential = pqBuf(potentialIndex)
    velocity(pqBuf, vBuf, metric)
    val toKinetic = dot(vBuf, pqBuf) / 2.0

    val fromPotential = params(potentialIndex)
    velocity(params, vBuf2, metric)
    val fromKinetic = dot(vBuf2, params) / 2.0

    val deltaH = toKinetic + toPotential - fromKinetic - fromPotential
    if (deltaH.isNaN) {
      Math.log(0.0)
    } else {
      val lap = (-deltaH).min(0.0)
      lap
    }
  }

  var iterationStartTime: Long = _
  def startIteration(params: Array[Double])(implicit rng: RNG): Unit = {
    initializePs(params)
    copy(params, pqBuf)
    iterationStartTime = System.nanoTime()
  }

  def finishIteration(params: Array[Double], metric: Metric)(
      implicit rng: RNG): Double = {
    val a = logAcceptanceProb(params, metric)
    if (a > Math.log(rng.standardUniform)) {
      copy(pqBuf, params)
    }

    stats.iterations += 1
    stats.iterationTimes.add((System.nanoTime() - iterationStartTime).toDouble)
    stats.acceptanceRates.add(Math.exp(a))
    a
  }

  def snapshot(out: Array[Double]): Unit =
    copy(pqBuf, out)

  def restore(in: Array[Double]): Unit =
    copy(in, pqBuf)

  // extract q
  def variables(params: Array[Double], out: Array[Double]): Unit = {
    var i = 0
    while (i < nVars) {
      out(i) = params(i + nVars)
      i += 1
    }
  }

  //we want the invariant that a params array always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential.
  def initialize()(implicit rng: RNG): Array[Double] = {
    val params = new Array[Double](inputOutputSize)
    java.util.Arrays.fill(pqBuf, 0.0)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      pqBuf(i) = rng.standardNormal
      i += 1
    }
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
    copy(pqBuf, params)
    initializePs(params)
    params
  }

  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
   */
  val nVars = density.nVars
  val potentialIndex = nVars * 2
  val inputOutputSize = potentialIndex + 1

  private val pqBuf = new Array[Double](inputOutputSize)
  private val qBuf = new Array[Double](nVars)
  private val vBuf = new Array[Double](nVars)
  private val vBuf2 = new Array[Double](nVars)

  private def newQs(stepSize: Double, metric: Metric): Unit = {
    velocity(pqBuf, vBuf, metric)
    var i = 0
    while (i < nVars) {
      pqBuf(i + nVars) += (stepSize * vBuf(i))
      i += 1
    }
  }

  private def halfPsNewQs(stepSize: Double, metric: Metric): Unit = {
    fullPs(stepSize / 2.0)
    newQs(stepSize, metric)
  }

  private def initialHalfThenFullStep(stepSize: Double,
                                      metric: Metric): Unit = {
    halfPsNewQs(stepSize, metric)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  private def fullPs(stepSize: Double): Unit = {
    copyQsAndUpdateDensity()
    var i = 0
    val j = nVars
    while (i < j) {
      pqBuf(i) += stepSize * density.gradient(i)
      i += 1
    }
  }

  private def fullPsNewQs(stepSize: Double, metric: Metric): Unit = {
    fullPs(stepSize)
    newQs(stepSize, metric)
  }

  private def twoFullSteps(stepSize: Double, metric: Metric): Unit = {
    fullPsNewQs(stepSize, metric)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  private def finalHalfStep(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  private def copy(sourceArray: Array[Double],
                   targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  private def copyQsAndUpdateDensity(): Unit = {
    System.arraycopy(pqBuf, nVars, qBuf, 0, nVars)
    val t = System.nanoTime()
    density.update(qBuf)
    stats.gradientTimes.add((System.nanoTime() - t).toDouble)
    stats.gradientEvaluations += 1
  }

  private def velocity(in: Array[Double], out: Array[Double], m: Metric): Unit =
    m match {
      case StandardMetric =>
        //System.arraycopy(in, 0, out, 0, out.size)
        var i = 0
        while (i < out.size) {
          out(i) = in(i) * 0.1
          i += 1
        }
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

  private def initializePs(params: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      params(i) = rng.standardNormal
      i += 1
    }
  }
}
