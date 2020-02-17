package com.stripe.rainier.sampler

class SamplerState(val chain: Int, density: DensityFunction, progress: Progress)(
    implicit val rng: RNG) {
  private val lf = LeapFrog(density)
  private val params = lf.initialize
  private var currentStepSize = 1.0
  private var pathLength = Iterator.continually(1)

  def startPhase(phase: String, iterations: Int): Unit = {
    currentPhase = phase
    phaseStartTime = System.nanoTime()
    phaseAcceptance = 0.0
    phasePathLength = 0L
    phaseIterations = iterations
    currentIteration = 0
    checkOutput()
  }

  def updateStepSize(e: Double): Unit = {
    currentStepSize = e
    checkOutput()
  }

  def stepSize: Double = currentStepSize

  def updatePathLength(it: Iterator[Int]): Unit = {
    pathLength = it
  }

  def step(): Double = {
    val l = pathLength.next
    val a = lf.step(params, l, stepSize)
    trackIteration(a, l)
    a
  }

  def tryStepping(): Double = {
    val a = lf.tryStepping(params, stepSize)
    trackIteration(a, 1)
    a
  }

  def longestBatchStep(): Int = {
    val (a, l) = lf.longestBatchStep(params, pathLength.next, stepSize)
    trackIteration(a, l)
    l
  }

  def variables: Array[Double] =
    lf.variables(params)

  def finish(): Unit =
    progress.finish(this)

  def isValid: Boolean = stepSize > 0.0

  def startGradient(): Unit = {
    lastGradientTime = System.nanoTime()
    checkOutput()
  }

  def endGradient(): Unit = {
    gradientTime += (System.nanoTime() - lastGradientTime)
    gradientEvaluations += 1
    checkOutput()
  }

  private def trackIteration(logAccept: Double, pathLength: Int): Unit = {
    phaseAcceptance += Math.exp(logAccept)
    phasePathLength += pathLength
    currentIteration += 1
    checkOutput()
  }

  val delayNanos = (progress.outputEverySeconds * 1e9).toLong
  private def checkOutput(): Unit = {
    val t = System.nanoTime()
    if ((t - lastOutputTime) > delayNanos) {
      progress.refresh(this)
      lastOutputTime = t
    }
  }

  var startTime = System.nanoTime()
  var phaseStartTime = 0L
  var currentPhase = ""
  var currentIteration = 0
  var phaseIterations = 0
  var gradientEvaluations = 0
  var gradientTime = 0L
  var lastGradientTime = 0L
  var lastOutputTime = 0L
  var phaseAcceptance = 0.0
  var phasePathLength = 0L
}
