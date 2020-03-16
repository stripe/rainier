package com.stripe.rainier.sampler

class EHMCSampler(minSteps: Int, maxSteps: Int, numLengths: Int, pCount: Double)
    extends Sampler {
  val totalLengths = new RingBuffer(numLengths)
  var buf: Array[Double] = _

  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG) = {
    buf = new Array[Double](lf.inputOutputSize)
  }

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             mass: MassMatrix)(implicit rng: RNG): Double = {
    lf.startIteration(params, mass)
    if (shouldCountSteps())
      countSteps(params, lf, stepSize, mass)
    else
      lf.takeSteps(nSteps(stepSize), stepSize, mass)
    lf.finishIteration(params, mass)
  }

  private def shouldCountSteps()(implicit rng: RNG): Boolean =
    rng.standardUniform < pCount

  private def countSteps(params: Array[Double],
                         lf: LeapFrog,
                         stepSize: Double,
                         mass: MassMatrix): Unit = {
    var l = 0
    while (!lf.isUTurn(params) && l < maxSteps) {
      l += 1
      lf.takeSteps(1, stepSize, mass)
      if (l == minSteps)
        lf.snapshot(buf)
    }
    if (l < minSteps) {
      lf.takeSteps(minSteps - l, stepSize, mass)
    } else {
      lf.restore(buf)
    }

    totalLengths.add(l * stepSize)
  }

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          mass: MassMatrix)(implicit rng: RNG): Unit = {
    lf.startIteration(params, mass)
    lf.takeSteps(nSteps(stepSize), stepSize, mass)
    lf.finishIteration(params, mass)
    ()
  }

  private def nSteps(stepSize: Double)(implicit rng: RNG): Int = {
    val length = totalLengths.sample()
    Math.ceil(length / stepSize).toInt
  }
}

object EHMC {
  def apply(warmIt: Int,
            it: Int,
            minSteps: Int = 10,
            numLengths: Int = 100): SamplerConfig =
    new SamplerConfig {
      val warmupIterations = warmIt
      val iterations = it
      val statsWindow = 100
      def sampler() = new EHMCSampler(minSteps, 32, numLengths, 0.1)
      def stepSizeTuner() = new DualAvgTuner(0.8)
      def massMatrixTuner() = new IdentityMassMatrixTuner
    }
}
