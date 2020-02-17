package com.stripe.rainier.sampler

class SamplerState(density: DensityFunction, progress: ProgressState)(
    implicit val rng: RNG) {
  progress.start()

  private val lf = LeapFrog(density, progress)
  private val params = lf.initialize
  private var currentStepSize = 1.0
  private var pathLength = Iterator.continually(1)

  progress.updateStepSize(currentStepSize)

  def startPhase(phase: String, iterations: Int): Unit =
    progress.startPhase(phase, iterations)

  def updateStepSize(e: Double): Unit = {
    currentStepSize = e
    progress.updateStepSize(stepSize)
  }

  def stepSize: Double = currentStepSize

  def updatePathLength(it: Iterator[Int]): Unit = {
    pathLength = it
  }

  def step(): Double =
    lf.step(params, pathLength.next, stepSize)

  def tryStepping(): Double =
    lf.tryStepping(params, stepSize)

  def longestBatchStep(): Int =
    lf.longestBatchStep(params, pathLength.next, stepSize)._2

  def variables: Array[Double] =
    lf.variables(params)

  def finish(): Unit =
    progress.finish()

  def isValid: Boolean = stepSize > 0.0
}
