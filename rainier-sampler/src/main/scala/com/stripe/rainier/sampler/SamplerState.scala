package com.stripe.rainier.sampler

class SamplerState(val chain: Int,
                   densityFn: DensityFunction,
                   val totalIterations: Int,
                   pathLength: PathLength,
                   progress: Progress)(implicit val rng: RNG) {
  val startTime = System.nanoTime()
  var currentIteration = 0
  var lastOutputTime = 0L

  var stepSize = 1.0
  var isWarmup = true

  private val lf = new LeapFrog(densityFn)
  private val params = lf.initialize

  progress.start(this)

  def findInitialStepSize(): Unit = {
    var logAcceptanceProb = lf.tryStepping(params, stepSize)
    val exponent = if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }
    val doubleOrHalf = Math.pow(2, exponent)

    while (stepSize != 0.0 && (exponent * logAcceptanceProb > -exponent * Math
             .log(2))) {
      stepSize *= doubleOrHalf
      logAcceptanceProb = lf.tryStepping(params, stepSize)
    }
  }

  def run(): Unit = {
    val findUTurn = isWarmup && ((currentIteration % pathLength.findUTurnEvery) == 0)
    if (findUTurn) {
      val n = pathLength.baseSteps
      lf.run(params, n, pathLength.stepSize(n, this), true)
    } else {
      val n = pathLength.nSteps(this)
      lf.run(params, n, pathLength.stepSize(n, this), false)
    }

    currentIteration += 1
    checkOutput()
  }

  def variables: Array[Double] = {
    val buf = new Array[Double](densityFn.nVars)
    lf.variables(params, buf)
    buf
  }

  def finish(): Unit =
    progress.finish(this)

  def isValid: Boolean = stepSize > 0.0

  def logAcceptanceProb = lf.recentAcceptProbs.last
  def gradientEvaluations = lf.gradientEvaluations
  def meanGradientTime = mean(lf.recentGradientTimes.toList).toLong
  def meanAcceptanceProb = mean(lf.recentAcceptProbs.toList.map(math.exp(_)))
  def meanStepCount = mean(lf.recentNSteps.toList)

  private def mean(list: List[Double]) =
    list.sum / list.size

  val delayNanos = (progress.outputEverySeconds * 1e9).toLong
  private def checkOutput(): Unit = {
    val t = System.nanoTime()
    if ((t - lastOutputTime) > delayNanos) {
      progress.refresh(this)
      lastOutputTime = t
    }
  }
}
