package com.stripe.rainier.sampler

class SamplerState(val chain: Int,
                   densityFn: DensityFunction,
                   progress: Progress)(implicit val rng: RNG) {

  def startPhase(phase: String, iterations: Int): Unit = {
    progress.refresh(this)

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

  def updateMetric(m: Metric): Unit = {
    currentMetric = m
    checkOutput()
  }

  def metric: Metric = currentMetric

  def updatePathLength(it: Iterator[Int]): Unit = {
    pathLength = it
  }

  def step(): Double = {
    val l = pathLength.next
    val a = lf.step(params, l, stepSize, metric)
    trackIteration(a, l)
    a
  }

  def tryStepping(): Double = {
    val a = lf.tryStepping(params, stepSize, metric)
    trackIteration(a, 1)
    a
  }

  def longestBatchStep(): Int = {
    val (a, l) = lf.longestBatchStep(params, pathLength.next, stepSize, metric)
    trackIteration(a, l)
    l
  }

  val nVars = densityFn.nVars
  def variables(out: Array[Double]): Unit =
    lf.variables(params, out)

  def finish(): Unit =
    progress.finish(this)

  def isValid: Boolean = stepSize > 0.0

  private def startGradient(): Unit = {
    lastGradientTime = System.nanoTime()
    checkOutput()
  }

  private def endGradient(): Unit = {
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

  private var currentStepSize = 1.0
  private var pathLength = Iterator.continually(1)
  private var currentMetric: Metric = StandardMetric

  private val densityWrapper = new DensityFunction {
    val nVars = densityFn.nVars
    def density = densityFn.density
    def gradient(index: Int) = densityFn.gradient(index)
    def update(vars: Array[Double]): Unit = {
      startGradient()
      densityFn.update(vars)
      endGradient()
    }
  }

  private val lf = LeapFrog(densityWrapper)
  private val params = lf.initialize
}
