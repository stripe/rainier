package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

case class Sampler(nSteps: Int, iterations: Int, warmupIterations: Int) {
  def sample(chain: Int, density: DensityFunction, progress: Progress)(
      implicit rng: RNG): List[Array[Double]] = {
    val state = new SamplerState(chain, density, progress)
    progress.start(state)

    findReasonableStepSize(state)
    if (!state.isValid) {
      WARNING.log("Initialization failed, aborting!")
      return Nil
    }

    warmup(state)
    val samples = run(state)
    state.finish()
    samples
  }

  private def run(state: SamplerState): List[Array[Double]] = {
    val buf = new ListBuffer[Array[Double]]
    var i = 0

    while (i < iterations) {
      state.step()
      val output = new Array[Double](state.nVars)
      state.variables(output)
      buf += output
      i += 1
    }
    buf.toList
  }

  private def warmup(state: SamplerState): Unit = {
    val dualAvg = DualAvg(0.65, state.stepSize)
    var i = 0
    while (i < warmupIterations) {
      state.updateStepSize(dualAvg.stepSize)
      val logAcceptanceProb = state.step()
      dualAvg.update(logAcceptanceProb)
      i += 1
    }
    state.updateStepSize(dualAvg.finalStepSize)
  }

  private def findReasonableStepSize(state: SamplerState): Unit = {
    var logAcceptanceProb = state.tryStepping()
    val exponent = if (logAcceptanceProb > Math.log(0.5)) { 1.0 } else { -1.0 }
    val doubleOrHalf = Math.pow(2, exponent)
    while (state.stepSize != 0.0 && (exponent * logAcceptanceProb > -exponent * Math
             .log(2))) {
      state.updateStepSize(state.stepSize * doubleOrHalf)
      logAcceptanceProb = state.tryStepping()
    }
  }
}

object Sampler {
  val default = Sampler(10, 1000, 10000)
}
