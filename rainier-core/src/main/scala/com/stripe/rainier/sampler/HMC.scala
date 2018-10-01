package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer

final case class HMC(nSteps: Int) extends Sampler {
  def sample(densityFun: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val lf = LeapFrog(densityFun)
    val params = lf.initialize
    val stepSize =
      DualAvg.findStepSize(lf, params, 0.65, nSteps, warmupIterations)
    if (stepSize == 0.0) //something's wrong, bail early
      List(lf.variables(params))
    else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0
      while (i < iterations) {
        lf.step(params, nSteps, stepSize)
        if (i % keepEvery == 0)
          buf += lf.variables(params)
        i += 1
      }
      buf.toList
    }
  }
}
