package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._
import java.util.concurrent.TimeUnit._

final case class HMC(nSteps: Int, warmupIterations: Int, iterations: Int)
    extends Sampler {
  def sample(density: DensityFunction)(
      implicit rng: RNG): List[Array[Double]] = {
    val lf = LeapFrog(density)
    val params = lf.initialize

    FINE.log("Finding reasonable initial step size")
    val stepSize0 = DualAvg.findReasonableStepSize(lf, params)
    FINE.log("Found initial step size of %f", stepSize0)

    FINE.log("Finding step size using %d warmup iterations", warmupIterations)
    val stepSize =
      DualAvg.findStepSize(0.65, stepSize0, warmupIterations) { ss =>
        lf.step(params, nSteps, ss)
      }
    FINE.log("Found step size of %f", stepSize)

    if (stepSize == 0.0) {
      WARNING.log("Found step size of 0.0, aborting!")
      List(lf.variables(params))
    } else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0
      FINE.log("Sampling for %d iterations", iterations)

      var acceptSum = 0.0
      while (i < iterations) {
        val logAccept = lf.step(params, nSteps, stepSize)
        acceptSum += Math.exp(logAccept)
        buf += lf.variables(params)
        i += 1
        FINER
          .atMostEvery(1, SECONDS)
          .log("Sampling iteration %d of %d, acceptance rate %f",
               i,
               iterations,
               (acceptSum / i))
      }
      FINE.log("Finished sampling, acceptance rate %f", (acceptSum / i))

      buf.toList
    }
  }
}
