package com.stripe.rainier.sampler

trait SamplerConfig {
  def iterations: Int
  def warmupIterations: Int

  def stepSizeTuner(): StepSizeTuner
  def metricTuner(): MetricTuner
  def sampler(): Sampler
}

object SamplerConfig {
  val default: SamplerConfig = HMC(10000, 1000, 5)
}

trait StepSizeTuner {
  def initialize(lf: LeapFrog)(implicit rng: RNG): Double
  def update(logAcceptanceProb: Double)(implicit rng: RNG): Double
  def reset()(implicit rng: RNG): Double
  def stepSize(implicit rng: RNG): Double
}

trait MetricTuner {
  def initialize(lf: LeapFrog): Metric
  def update(sample: Array[Double]): Option[Metric]
  def metric: Metric
}

trait Sampler {
  def initialize(lf: LeapFrog)(implicit rng: RNG): Unit
  def warmup(lf: LeapFrog, stepSize: Double, metric: Metric)(
      implicit rng: RNG): Double
  def run(lf: LeapFrog, stepSize: Double, metric: Metric)(
      implicit rng: RNG): Unit
}
