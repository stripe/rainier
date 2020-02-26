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
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG): Double
  def update(logAcceptanceProb: Double)(implicit rng: RNG): Double
  def reset(params: Array[Double], lf: LeapFrog, metric: Metric)(
      implicit rng: RNG): Double
  def stepSize(implicit rng: RNG): Double
}

trait MetricTuner {
  def initialize(lf: LeapFrog): Metric
  def update(sample: Array[Double]): Option[Metric]
  def metric: Metric
}

trait Sampler {
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG): Unit
  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             metric: Metric)(implicit rng: RNG): Double
  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          metric: Metric)(implicit rng: RNG): Unit

  def jitter(factor: Double = 1.0): Sampler =
    Jitter(this, factor)
}

case class Jitter(orig: Sampler, jitterFactor: Double) extends Sampler {
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG): Unit =
    orig.initialize(params, lf)

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             metric: Metric)(implicit rng: RNG): Double =
    orig.warmup(params, lf, stepSize, metric)

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          metric: Metric)(implicit rng: RNG): Unit =
    orig.run(params, lf, jitter(stepSize), metric)

  private def jitter(stepSize: Double)(implicit rng: RNG): Double = {
    val u = 1 - rng.standardUniform * 2
    stepSize + (stepSize * u)
  }
}
