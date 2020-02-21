package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

case class Sampler(warmupIterations: Int,
                   iterations: Int,
                   pathLength: PathLength) {
  def sample(chain: Int, density: DensityFunction, progress: Progress)(
      implicit rng: RNG) = {
    val state = new SamplerState(chain,
                                 density,
                                 warmupIterations + iterations,
                                 pathLength,
                                 progress)

    state.findInitialStepSize()
    warmup(state)

    val samples =
      if (state.isValid) {
        run(state)
      } else {
        WARNING.log("Warmup failed, aborting!")
        List(state.variables)
      }

    state.finish()
    samples
  }

  private def warmup(state: SamplerState): Unit = {
    val dualAvg = DualAvg(0.65, state.stepSize)
    var i = 0
    while (i < warmupIterations && state.isValid) {
      state.stepSize = dualAvg.stepSize
      state.run()
      dualAvg.update(state.logAcceptanceProb)
      i += 1
    }
    state.stepSize = dualAvg.finalStepSize
    state.isWarmup = false
  }

  private def run(state: SamplerState): List[Array[Double]] = {
    val buf = new ListBuffer[Array[Double]]
    var i = 0
    while (i < iterations) {
      state.run()
      buf += state.variables
      i += 1
    }
    buf.toList
  }
}

object Sampler {
  val default =
    EHMC(warmupIterations = 10000, iterations = 10000, l0 = 10)
}

//backwards compatibility
object HMC {
  def apply(warmupIterations: Int, iterations: Int, nSteps: Int): Sampler =
    Sampler(warmupIterations, iterations, FixedStepCount(nSteps))
}

object EHMC {
  def apply(warmupIterations: Int, iterations: Int, l0: Int = 10): Sampler =
    HMC(warmupIterations, iterations, l0)
}
