package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer

object Driver {
  def sample(chain: Int,
             config: SamplerConfig,
             density: DensityFunction,
             progress: Progress)(implicit rng: RNG): List[Array[Double]] = {

    val sampler = config.sampler()
    val stepSizeTuner = config.stepSizeTuner()
    val metricTuner = config.metricTuner()

    val lf = new LeapFrog(density)

    progress.start(chain, lf.stats)

    warmup(chain,
           lf,
           sampler,
           stepSizeTuner,
           metricTuner,
           config.warmupIterations,
           progress)
    val samples = collectSamples(chain,
                                 lf,
                                 sampler,
                                 stepSizeTuner,
                                 metricTuner,
                                 config.iterations,
                                 progress)

    progress.finish(chain, lf.stats)
    samples
  }

  private def warmup(chain: Int,
                     lf: LeapFrog,
                     sampler: Sampler,
                     stepSizeTuner: StepSizeTuner,
                     metricTuner: MetricTuner,
                     iterations: Int,
                     progress: Progress)(implicit rng: RNG): Unit = {
    var i = 0
    var nextOutputTime = System.nanoTime()

    sampler.initialize(lf)
    var stepSize = stepSizeTuner.initialize(lf)
    var metric = metricTuner.initialize(lf)

    val sample = new Array[Double](lf.nVars)

    while (i < iterations) {
      val logAcceptProb = sampler.warmup(lf, stepSize, metric)
      stepSize = stepSizeTuner.update(logAcceptProb)
      lf.variables(sample)
      metricTuner.update(sample) match {
        case Some(m) =>
          metric = m
          stepSize = stepSizeTuner.reset()
        case None => ()
      }
      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, lf.stats)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
  }

  private def collectSamples(
      chain: Int,
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
      sampler.run(lf, stepSizeTuner.stepSize, metricTuner.metric)
      val output = new Array[Double](lf.nVars)
      lf.variables(output)
      buf += output

      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, lf.stats)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }
    buf.toList
  }
}
