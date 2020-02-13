package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

final case class HMC(warmupIterations: Int, iterations: Int, nSteps: Int)
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
        lf.step(params, nSteps, ss)
      }
    progress.updateStepSize(stepSize)

    if (stepSize == 0.0) {
      WARNING.log("Found step size of 0.0, aborting!")
      List(lf.variables(params))
    } else {
      val buf = new ListBuffer[Array[Double]]
      var i = 0

      progress.startPhase("Sampling", iterations)
      while (i < iterations) {
        lf.step(params, nSteps, stepSize)
        buf += lf.variables(params)
        i += 1
      }

      buf.toList
    }
  }
}
