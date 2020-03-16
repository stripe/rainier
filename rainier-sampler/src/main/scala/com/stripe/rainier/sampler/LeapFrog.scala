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
                  mass: MassMatrix): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize, mass)
    finalHalfStep(stepSize)
    val deltaH = energy(pqBuf, mass) - energy(params, mass)
    logAcceptanceProb(deltaH)
  }

  def takeSteps(l: Int, stepSize: Double, mass: MassMatrix): Unit = {
    stats.stepSizes.add(stepSize)
    initialHalfThenFullStep(stepSize, mass)
    var i = 1
    while (i < l) {
      twoFullSteps(stepSize, mass)
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
  def startIteration(params: Array[Double], mass: MassMatrix)(
      implicit rng: RNG): Unit = {
    prevH = energy(params, mass)
    initializePs(params, mass)
    copy(params, pqBuf)
    iterationStartTime = System.nanoTime()
    iterationStartGrads = stats.gradientEvaluations
  }

  def finishIteration(params: Array[Double], mass: MassMatrix)(
      implicit rng: RNG): Double = {
    val startH = energy(params, mass)
    val endH = energy(pqBuf, mass)
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
  def initialize(mass: MassMatrix)(implicit rng: RNG): Array[Double] = {
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
    initializePs(params, mass)
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
  private val buf = new Array[Double](nVars)

  private def energy(params: Array[Double], mass: MassMatrix): Double = {
    val potential = params(potentialIndex)
    velocity(params, buf, mass)
    val kinetic = dot(buf, params) / 2.0
    potential + kinetic
  }

  private def logAcceptanceProb(deltaH: Double): Double =
    if (deltaH.isNaN)
      Math.log(0.0)
    else
      (-deltaH).min(0.0)

  private def newQs(stepSize: Double, mass: MassMatrix): Unit = {
    velocity(pqBuf, buf, mass)
    var i = 0
    while (i < nVars) {
      pqBuf(i + nVars) += (stepSize * buf(i))
      i += 1
    }
  }

  private def halfPsNewQs(stepSize: Double, mass: MassMatrix): Unit = {
    fullPs(stepSize / 2.0)
    newQs(stepSize, mass)
  }

  private def initialHalfThenFullStep(stepSize: Double,
                                      mass: MassMatrix): Unit = {
    halfPsNewQs(stepSize, mass)
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

  private def fullPsNewQs(stepSize: Double, mass: MassMatrix): Unit = {
    fullPs(stepSize)
    newQs(stepSize, mass)
  }

  private def twoFullSteps(stepSize: Double, mass: MassMatrix): Unit = {
    fullPsNewQs(stepSize, mass)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  private def finalHalfStep(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
  }

  private def copy(sourceArray: Array[Double],
                   targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  private def copyQsAndUpdateDensity(): Unit = {
    System.arraycopy(pqBuf, nVars, buf, 0, nVars)
    val t = System.nanoTime()
    density.update(buf)
    stats.gradientTimes.add((System.nanoTime() - t).toDouble)
    stats.gradientEvaluations += 1
  }

  private def velocity(in: Array[Double],
                       out: Array[Double],
                       mass: MassMatrix): Unit =
    mass match {
      case IdentityMassMatrix =>
        System.arraycopy(in, 0, out, 0, out.size)
      case DiagonalMassMatrix(elements) =>
        var i = 0
        while (i < out.size) {
          out(i) = in(i) * elements(i)
          i += 1
        }
      case DenseMassMatrix(elements) =>
        DenseMassMatrix.squareMultiply(elements, in, out)
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

  private def initializePs(params: Array[Double], mass: MassMatrix)(
      implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      buf(i) = rng.standardNormal
      i += 1
    }

    mass match {
      case IdentityMassMatrix =>
        System.arraycopy(buf, 0, params, 0, nVars)
      case d: DiagonalMassMatrix =>
        val stdDevs = d.stdDevs
        i = 0
        while (i < nVars) {
          params(i) = buf(i) / stdDevs(i)
          i += 1
        }
      case f: DenseMassMatrix =>
        val u = f.choleskyUpperTriangular
        DenseMassMatrix.upperTriangularSolve(u, buf, params)
    }
  }
}
