package com.stripe.rainier.sampler

trait SamplerConfig {
  def iterations: Int
  def warmupIterations: Int
  def statsWindow: Int

  def stepSizeTuner(): StepSizeTuner
  def massMatrixTuner(): MassMatrixTuner
  def sampler(): Sampler
}

object SamplerConfig {
  val default: SamplerConfig = HMC(10000, 1000, 5)
}

trait StepSizeTuner {
  def initialize(params: Array[Double], lf: LeapFrog): Double
  def update(logAcceptanceProb: Double): Double
  def reset(): Double
  def stepSize: Double
}

case class StaticStepSize(stepSize: Double) extends StepSizeTuner {
  def initialize(params: Array[Double], lf: LeapFrog) = stepSize
  def update(logAcceptanceProb: Double) = stepSize
  def reset() = stepSize
}

trait MassMatrixTuner {
  def initialize(lf: LeapFrog, iterations: Int): MassMatrix
  def update(sample: Array[Double]): Option[MassMatrix]
}

case class StaticMassMatrix(mass: MassMatrix) extends MassMatrixTuner {
  def initialize(lf: LeapFrog, iterations: Int) = mass
  def update(sample: Array[Double]) = None
}

trait Sampler {
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG): Unit
  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             mass: MassMatrix)(implicit rng: RNG): Double
  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          mass: MassMatrix)(implicit rng: RNG): Unit
}
