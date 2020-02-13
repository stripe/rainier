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

    progress.start()
    progress.startPhase("Finding reasonable initial step size", 0)
    val stepSize0 = DualAvg.findReasonableStepSize(lf, params)
    progress.updateStepSize(stepSize0)

    progress.startPhase("Finding step size using dual averaging",
                        warmupIterations)
    val stepSize =
      DualAvg.findStepSize(0.65, stepSize0, warmupIterations) { ss =>
        progress.updateStepSize(ss)
        lf.step(params, 1, ss)
      }
    progress.updateStepSize(stepSize)

    progress.startPhase("Sampling path lengths", k)
    val empiricalL = Vector.fill(k) {
      lf.longestBatchStep(params, l0, stepSize)._2
    }
    val sorted = empiricalL.toList.sorted

    if (stepSize == 0.0) {
      progress.finish()
      List(lf.variables(params))
    }
    else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0

      progress.startPhase("Sampling", iterations)
      while (i < iterations) {
        val j = rng.int(k)
        val nSteps = empiricalL(j)
        val logAccept = lf.step(params, nSteps, stepSize)
        acceptSum += Math.exp(logAccept)
        buf += lf.variables(params)
        i += 1
      }
      progress.finish()
      buf.toList
    }
  }
}
