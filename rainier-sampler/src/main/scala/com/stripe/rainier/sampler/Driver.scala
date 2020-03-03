package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

object Driver {
  def sample(chain: Int,
             config: SamplerConfig,
             density: DensityFunction,
             progress: Progress)(implicit rng: RNG): List[Array[Double]] = {

    val sampler = config.sampler()
    val stepSizeTuner = config.stepSizeTuner()
    val metricTuner = config.metricTuner()

    val lf = new LeapFrog(density, config.statsWindow)

    progress.start(chain, "Initializing", lf.stats)

    FINE.log("Starting warmup")
    val params = warmup(chain,
                        lf,
                        sampler,
                        stepSizeTuner,
                        metricTuner,
                        config.warmupIterations,
                        progress)
    lf.resetStats()
    FINE.log("Starting sampling")
    val samples = collectSamples(chain,
                                 params,
                                 lf,
                                 sampler,
                                 stepSizeTuner,
                                 metricTuner,
                                 config.iterations,
                                 progress)

    FINE.log("Finished sampling")

    progress.finish(chain, "Complete", lf.stats)
    samples
  }

  private def warmup(chain: Int,
                     lf: LeapFrog,
                     sampler: Sampler,
                     stepSizeTuner: StepSizeTuner,
                     metricTuner: MetricTuner,
                     iterations: Int,
                     progress: Progress)(implicit rng: RNG): Array[Double] = {
    var i = 0
    var nextOutputTime = System.nanoTime()

    val params = lf.initialize()
    sampler.initialize(params, lf)
    var stepSize = stepSizeTuner.initialize(params, lf)
    var metric = metricTuner.initialize(lf)

    FINER.log("Initial step size %f", stepSize)

    val sample = new Array[Double](lf.nVars)

    while (i < iterations) {
      val logAcceptProb = sampler.warmup(params, lf, stepSize, metric)
      stepSize = stepSizeTuner.update(logAcceptProb)

      FINEST.log("Accept probability %f", Math.exp(logAcceptProb))
      FINEST.log("Adapted step size %f", stepSize)

      lf.variables(params, sample)
      metricTuner.update(sample) match {
        case Some(m) =>
          metric = m
          stepSize = stepSizeTuner.reset()
        case None => ()
      }
      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, "Warmup", lf.stats)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
    params
  }

  private def collectSamples(
      chain: Int,
      params: Array[Double],
      lf: LeapFrog,
      sampler: Sampler,
      stepSizeTuner: StepSizeTuner,
      metricTuner: MetricTuner,
      iterations: Int,
      progress: Progress)(implicit rng: RNG): List[Array[Double]] = {
    var nextOutputTime = System.nanoTime()
    val buf = new ListBuffer[Array[Double]]
    var i = 0
    while (i < iterations) {
      sampler.run(params, lf, stepSizeTuner.stepSize, metricTuner.metric)
      val output = new Array[Double](lf.nVars)
      lf.variables(params, output)
      buf += output

      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, "Sampling", lf.stats)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
    buf.toList
  }
}
