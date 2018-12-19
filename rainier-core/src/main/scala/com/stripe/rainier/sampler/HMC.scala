package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._
import java.util.concurrent.TimeUnit._

final case class HMC(nSteps: Int) extends Sampler {
  def sample(density: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val lf = LeapFrog(density)
    val params = lf.initialize

    FINE.log("Finding step size using %d warmup iterations", warmupIterations)
    val stepSize =
      DualAvg.findStepSize(lf, params, 0.65, nSteps, warmupIterations)
    FINE.log("Found step size of %f", stepSize)

    if (stepSize == 0.0) {
      WARNING.log("Found step size of 0.0, aborting!")
      List(lf.variables(params))
    } else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0
      FINE.log("Sampling for %d iterations", iterations)

      while (i < iterations) {
        FINER
          .atMostEvery(1, SECONDS)
          .log("Sampling iteration %d of %d", i, iterations)
        lf.step(params, nSteps, stepSize)
        if (i % keepEvery == 0)
          buf += lf.variables(params)
        i += 1
      }
      FINE.log("Finished sampling")

      buf.toList
    }
  }
}
