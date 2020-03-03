package com.stripe.rainier.sampler

final class LeapFrog(density: DensityFunction, statsWindow: Int) {
  var stats = new Stats(statsWindow)

  def resetStats(): Stats = {
    val oldStats = stats
    stats = new Stats(statsWindow)
    oldStats
  }

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double],
                  stepSize: Double,
                  metric: Metric): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize, metric)
    finalHalfStep(stepSize)
    val deltaH = energy(pqBuf, metric) - energy(params, metric)
    logAcceptanceProb(deltaH)
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

  var iterationStartTime: Long = _
  var iterationStartGrads: Long = _
  var prevH: Double = _
  def startIteration(params: Array[Double], metric: Metric)(
      implicit rng: RNG): Unit = {
    prevH = energy(params, metric)
    initializePs(params)
    copy(params, pqBuf)
    iterationStartTime = System.nanoTime()
    iterationStartGrads = stats.gradientEvaluations
  }

  def finishIteration(params: Array[Double], metric: Metric)(
      implicit rng: RNG): Double = {
    val startH = energy(params, metric)
    val endH = energy(pqBuf, metric)
    val deltaH = endH - startH
    val a = logAcceptanceProb(deltaH)
    if (a > Math.log(rng.standardUniform)) {
      copy(pqBuf, params)
      stats.energyVariance.update(endH)
      stats.energyTransitions2 += Math.pow(endH - prevH, 2)
    } else {
      stats.energyVariance.update(startH)
      stats.energyTransitions2 += Math.pow(startH - prevH, 2)
    }

    stats.iterations += 1
    stats.iterationTimes.add((System.nanoTime() - iterationStartTime).toDouble)
    stats.acceptanceRates.add(Math.exp(a))
    stats.gradsPerIteration.add(
      (stats.gradientEvaluations - iterationStartGrads).toDouble)
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

  private def energy(params: Array[Double], metric: Metric): Double = {
    val potential = params(potentialIndex)
    velocity(params, vBuf, metric)
    val kinetic = dot(vBuf, params) / 2.0
    potential + kinetic
  }

  private def logAcceptanceProb(deltaH: Double): Double =
    if (deltaH.isNaN)
      Math.log(0.0)
    else
      (-deltaH).min(0.0)

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

  private def velocity(in: Array[Double],
                       out: Array[Double],
                       metric: Metric): Unit =
    metric match {
      case StandardMetric =>
        System.arraycopy(in, 0, out, 0, out.size)
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
