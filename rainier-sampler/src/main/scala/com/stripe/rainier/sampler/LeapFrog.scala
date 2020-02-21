package com.stripe.rainier.sampler

private[sampler] class LeapFrog(density: DensityFunction) {
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

  //stats
  var gradientEvaluations = 0L
  val recentStepSizes = new RingBuffer(100)
  val recentNSteps = new RingBuffer(100)
  val recentUTurnSteps = new RingBuffer(100)
  val recentAcceptProbs = new RingBuffer(100)
  val recentGradientTimes = new RingBuffer(100)

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

  // extract q
  def variables(array: Array[Double], out: Array[Double]): Unit = {
    var i = 0
    while (i < nVars) {
      out(i) = array(i + nVars)
      i += 1
    }
  }

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double], stepSize: Double): Double = {
    copy(params, pqBuf)
    initialHalfThenFullStep(stepSize)
    finalHalfStep(stepSize)
    logAcceptanceProb(params, pqBuf)
  }

  //take multiple steps
  //this will always clobber the ps in params,
  //but will only update the qs and potential if the move is accepted
  def run(params: Array[Double],
          nSteps: Int,
          stepSize: Double,
          findUTurn: Boolean)(implicit rng: RNG): Unit = {
    recentNSteps.add(nSteps.toDouble)
    recentStepSizes.add(stepSize)

    initializePs(params)
    copy(params, pqBuf)
    if (findUTurn) {
      val l = longestStepUntilUTurn(params, nSteps, stepSize)
      recentUTurnSteps.add(l.toDouble)
    } else {
      steps(nSteps, stepSize)
    }

    val a = logAcceptanceProb(params, pqBuf)
    recentAcceptProbs.add(a)

    if (math.log(rng.standardUniform) < a)
      copy(pqBuf, params)
  }

  private def steps(l: Int, stepSize: Double): Unit = {
    initialHalfThenFullStep(stepSize)
    var i = 1
    while (i < l) {
      twoFullSteps(stepSize)
      i += 1
    }
    finalHalfStep(stepSize)
  }

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

  private def newQs(stepSize: Double): Unit = {
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      pqBuf(i) += (stepSize * pqBuf(i - nVars))
      i += 1
    }
  }

  private def halfPsNewQs(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
    newQs(stepSize)
  }

  private def initialHalfThenFullStep(stepSize: Double): Unit = {
    halfPsNewQs(stepSize)
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

  private def fullPsNewQs(stepSize: Double): Unit = {
    fullPs(stepSize)
    newQs(stepSize)
  }

  private def twoFullSteps(stepSize: Double): Unit = {
    fullPsNewQs(stepSize: Double)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

  private def finalHalfStep(stepSize: Double): Unit = {
    fullPs(stepSize / 2.0)
    copyQsAndUpdateDensity()
    pqBuf(potentialIndex) = density.density * -1
  }

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

  private def copy(sourceArray: Array[Double],
                   targetArray: Array[Double]): Unit =
    System.arraycopy(sourceArray, 0, targetArray, 0, inputOutputSize)

  private def copyQsAndUpdateDensity(): Unit = {
    System.arraycopy(pqBuf, nVars, qBuf, 0, nVars)
    val t = System.nanoTime()
    density.update(qBuf)
    val dt = System.nanoTime() - t
    recentGradientTimes.add(dt.toDouble)
    gradientEvaluations += 1
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

class RingBuffer(size: Int) {
  private var i = 0
  private var full = false
  private val buf = new Array[Double](size)

  def add(value: Double): Unit = {
    i += 1
    if (i == size)
      full = true
    i = i % size
    buf(i) = value
  }

  def last: Double = buf(i)
  def toList: List[Double] = {
    val lastPart = buf.take(i).toList
    if (full)
      buf.drop(i).toList ++ lastPart
    else
      lastPart
  }
}
