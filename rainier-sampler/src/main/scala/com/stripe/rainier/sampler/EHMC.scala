package com.stripe.rainier.sampler

class EHMCSampler(minSteps: Int, maxSteps: Int, numLengths: Int, pCount: Double)
    extends Sampler {
  val lengths = new RingBuffer(numLengths)
  var buf: Array[Double] = _

  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG) = {
    buf = new Array[Double](lf.inputOutputSize)
  }

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             metric: Metric)(implicit rng: RNG): Double = {
    lf.startIteration(params)
    if (shouldCountSteps())
      countSteps(params, lf, stepSize, metric)
    else
      lf.takeSteps(nSteps(), stepSize, metric)
    lf.finishIteration(params, metric)
  }

  private def shouldCountSteps()(implicit rng: RNG): Boolean =
    rng.standardUniform < pCount

  private def countSteps(params: Array[Double],
                         lf: LeapFrog,
                         stepSize: Double,
                         metric: Metric): Unit = {
    var l = 0
    while ((l < maxSteps) && !lf.isUTurn(params)) {
      l += 1
      lf.takeSteps(1, stepSize, metric)
      if (l == minSteps)
        lf.snapshot(buf)
    }
    if (l < minSteps) {
      lf.takeSteps(minSteps - l, stepSize, metric)
    } else {
      lf.restore(buf)
    }

    lengths.add(l.toDouble)
  }

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          metric: Metric)(implicit rng: RNG): Unit = {
    lf.startIteration(params)
    lf.takeSteps(nSteps(), stepSize, metric)
    lf.finishIteration(params, metric)
    ()
  }

  private def nSteps()(implicit rng: RNG): Int =
    lengths.sample().toInt
}

object EHMC {
  def apply(warmIt: Int,
            it: Int,
            minSteps: Int = 10,
            numLengths: Int = 100): SamplerConfig =
    new SamplerConfig {
      val warmupIterations = warmIt
      val iterations = it
      def sampler() = new EHMCSampler(minSteps, 32, numLengths, 0.1).jitter()
      def stepSizeTuner() = new DualAvgTuner(0.65)
      def metricTuner() = new DiagonalMetricTuner(20, 1.5)
    }
}
