package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._
import java.util.concurrent.TimeUnit._

/**
  * Empirical HMC - automated tuning of step size and number of leapfrog steps
  * @param l0 the initial number of leapfrog steps to use during the dual averaging phase
  * @param k the number of iterations to use when determining the
  * empirical distribution of the total number of leapfrog steps until a u-turn
  */
final case class EHMC(warmupIterations: Int,
                      iterations: Int,
                      l0: Int = 10,
                      k: Int = 1000)
    extends Sampler {
  def sample(density: DensityFunction, progress: ProgressState)(
      implicit rng: RNG): List[Array[Double]] = {
    if (density.nVars == 0)
      return List.fill(iterations)(Array.empty[Double])

    val lf = LeapFrog(density, progress)
    val params = lf.initialize

    FINE.log("Finding reasonable initial step size")
    val stepSize0 = DualAvg.findReasonableStepSize(lf, params)
    FINE.log("Found initial step size of %f", stepSize0)

    FINE.log("Warming up for %d iterations", warmupIterations)
    val stepSize =
      DualAvg.findStepSize(0.65, stepSize0, warmupIterations) { ss =>
        lf.step(params, 1, ss)
      }
    FINE.log("Found step size of %f", stepSize)

    FINE.log("Sampling %d path lengths", k)
    val empiricalL = Vector.fill(k) {
      lf.longestBatchStep(params, l0, stepSize)._2
    }
    val sorted = empiricalL.toList.sorted
    FINE.log("Using a range of %d to %d steps", sorted.head, sorted.last)

    if (stepSize == 0.0)
      List(lf.variables(params))
    else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0

      FINE.log("Sampling for %d iterations", iterations)

      var acceptSum = 0.0
      while (i < iterations) {
        val j = rng.int(k)
        val nSteps = empiricalL(j)

        FINER
          .atMostEvery(1, SECONDS)
          .log("Sampling iteration %d of %d for %d steps, acceptance rate %f",
               i,
               iterations,
               nSteps,
               (acceptSum / i))

        val logAccept = lf.step(params, nSteps, stepSize)
        acceptSum += Math.exp(logAccept)
        buf += lf.variables(params)
        i += 1
      }
      FINE.log("Finished sampling, acceptance rate %f", (acceptSum / i))
      buf.toList
    }
  }
}
