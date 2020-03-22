package com.stripe.rainier.sampler

class HMCSampler(nSteps: Int) extends Sampler {
  def initialize(params: Array[Double], lf: LeapFrog)(implicit rng: RNG) = ()

  def warmup(params: Array[Double],
             lf: LeapFrog,
             stepSize: Double,
             mass: MassMatrix)(implicit rng: RNG): Double = {
    lf.startIteration(params, mass)
    lf.takeSteps(nSteps, stepSize, mass)
    lf.finishIteration(params, mass)
  }

  def run(params: Array[Double],
          lf: LeapFrog,
          stepSize: Double,
          mass: MassMatrix)(implicit rng: RNG): Unit = {
    lf.startIteration(params, mass)
    lf.takeSteps(nSteps, stepSize, mass)
    lf.finishIteration(params, mass)
    ()
  }
}

object HMC {
  def apply(warmIt: Int, it: Int, nSteps: Int): SamplerConfig =
    new DefaultConfig {
      override val warmupIterations = warmIt
      override val iterations = it
      override def sampler() = new HMCSampler(nSteps)
    }
}
