package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

object Driver {
  def sample(chain: Int,
             config: SamplerConfig,
             density: DensityFunction,
             progress: Progress)(
      implicit rng: RNG): (List[Array[Double]], MassMatrix, Stats) = {

    val sampler = config.sampler()
    val stepSizeTuner = config.stepSizeTuner()
    val massMatrixTuner = config.massMatrixTuner()

    val lf = new LeapFrog(density, config.statsWindow)

    progress.start(chain)

    FINE.log("Starting warmup")
    val params = lf.initialize(IdentityMassMatrix) //TODO
    val mass = warmup(chain,
                      params,
                      lf,
                      sampler,
                      stepSizeTuner,
                      massMatrixTuner,
                      config.warmupIterations,
                      progress)
    lf.resetStats()
    FINE.log("Starting sampling")
    val samples = collectSamples(chain,
                                 params,
                                 lf,
                                 sampler,
                                 stepSizeTuner.stepSize,
                                 mass,
                                 config.iterations,
                                 progress)

    FINE.log("Finished sampling")

    progress.finish(chain, "Complete", lf.stats, mass)
    (samples, mass, lf.stats)
  }

  private def warmup(chain: Int,
                     params: Array[Double],
                     lf: LeapFrog,
                     sampler: Sampler,
                     stepSizeTuner: StepSizeTuner,
                     massMatrixTuner: MassMatrixTuner,
                     iterations: Int,
                     progress: Progress)(implicit rng: RNG): MassMatrix = {
    var i = 0
    var nextOutputTime = System.nanoTime()

    sampler.initialize(params, lf)
    var stepSize = stepSizeTuner.initialize(params, lf)
    var mass = massMatrixTuner.initialize(lf, iterations)

    FINER.log("Initial step size %f", stepSize)

    val sample = new Array[Double](lf.nVars)

    while (i < iterations) {
      val logAcceptProb = sampler.warmup(params, lf, stepSize, mass)
      stepSize = stepSizeTuner.update(logAcceptProb)

      FINEST.log("Accept probability %f", Math.exp(logAcceptProb))
      FINEST.log("Adapted step size %f", stepSize)

      lf.variables(params, sample)
      massMatrixTuner.update(sample) match {
        case Some(m) =>
          mass = m
          stepSize = stepSizeTuner.reset()
        case None => ()
      }
      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, "Warmup", lf.stats, mass)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
    mass
  }

  private def collectSamples(
      chain: Int,
      params: Array[Double],
      lf: LeapFrog,
      sampler: Sampler,
      stepSize: Double,
      mass: MassMatrix,
      iterations: Int,
      progress: Progress)(implicit rng: RNG): List[Array[Double]] = {
    var nextOutputTime = System.nanoTime()
    val buf = new ListBuffer[Array[Double]]
    var i = 0
    while (i < iterations) {
      sampler.run(params, lf, stepSize, mass)
      val output = new Array[Double](lf.nVars)
      lf.variables(params, output)
      buf += output

      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, "Sampling", lf.stats, mass)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
    buf.toList
  }
}
