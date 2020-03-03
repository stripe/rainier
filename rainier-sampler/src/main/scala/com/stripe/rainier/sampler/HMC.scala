package com.stripe.rainier.sampler

class HMCSampler(nSteps: Int) extends Sampler {
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG) = ()

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             metric: Metric)(implicit rng: RNG): Double = {
    lf.startIteration(params, metric)
    lf.takeSteps(nSteps, stepSize, metric)
    lf.finishIteration(params, metric)
  }

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          metric: Metric)(implicit rng: RNG): Unit = {
    lf.startIteration(params, metric)
    lf.takeSteps(nSteps, stepSize, metric)
    lf.finishIteration(params, metric)
    ()
  }
}

object HMC {
  def apply(warmIt: Int, it: Int, nSteps: Int): SamplerConfig =
    new SamplerConfig {
      val warmupIterations = warmIt
      val iterations = it
      val statsWindow = 100
      def sampler() = new HMCSampler(nSteps)
      def stepSizeTuner() = new DualAvgTuner(0.65)
      def metricTuner() = new StandardMetricTuner()
    }
}
