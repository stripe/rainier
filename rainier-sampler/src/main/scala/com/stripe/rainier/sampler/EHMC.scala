package com.stripe.rainier.sampler

class EHMCSampler(maxSteps: Int,
                  minSteps: Int = 1,
                  bufSize: Int = 100,
                  pCount: Double = 0.1)
    extends Sampler {
  val steps = new RingBuffer(bufSize)
  var buf: Array[Double] = _

  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG) = {
    buf = new Array[Double](lf.inputOutputSize)
  }

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             mass: MassMatrix)(implicit rng: RNG): Double = {
    lf.startIteration(params, mass)
    if (shouldCountSteps()) {
      countSteps(params, lf, stepSize, mass)
    } else {
      val n = steps.sample().toInt
      lf.takeSteps(n, stepSize, mass)
    }
    lf.finishIteration(params, mass)
  }

  private def shouldCountSteps()(implicit rng: RNG): Boolean =
    !steps.full || rng.standardUniform < pCount

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

    steps.add(l.toDouble)
  }

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          mass: MassMatrix)(implicit rng: RNG): Unit = {
    lf.startIteration(params, mass)
    val n = steps.sample().toInt
    lf.takeSteps(n, stepSize, mass)
    lf.finishIteration(params, mass)
    ()
  }
}

object EHMC {
  def apply(warmIt: Int,
            it: Int,
            minSteps: Int = 1,
            numLengths: Int = 100): SamplerConfig =
    new DefaultConfig {
      override val warmupIterations = warmIt
      override val iterations = it
      override def sampler() = new EHMCSampler(1000, minSteps, numLengths, 0.1)
    }
}
