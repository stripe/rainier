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

    val lf = new LeapFrog(density)

    progress.start(chain, lf.stats)

    FINE.log("Starting warmup")
    val params = lf.initialize()

    val metric = warmup(chain,
                        params,
                        lf,
                        sampler,
                        stepSizeTuner,
                        metricTuner,
                        config.warmupIterations,
                        progress)

    FINE.log("Starting sampling")
    val samples = collectSamples(chain,
                                 params,
                                 lf,
                                 sampler,
                                 stepSizeTuner.stepSize,
                                 metric,
                                 config.iterations,
                                 progress)

    FINE.log("Finished sampling")

    progress.finish(chain, lf.stats)
    samples
  }

  private def warmup(chain: Int,
                     params: Array[Double],
                     lf: LeapFrog,
                     sampler: Sampler,
                     stepSizeTuner: StepSizeTuner,
                     metricTuner: MetricTuner,
                     iterations: Int,
                     progress: Progress)(implicit rng: RNG): Metric = {
    var i = 0
    var nextOutputTime = System.nanoTime()

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
        case Some(m) if ((i > 50) && (iterations - i > 50)) =>
          metric = m
          stepSize = stepSizeTuner.reset(params, lf, metric)
        case _ => ()
      }
      if (System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, lf.stats)
        nextOutputTime = System
          .nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }

      i += 1
    }

    metric
  }

  private def collectSamples(
      chain: Int,
      params: Array[Double],
      lf: LeapFrog,
      sampler: Sampler,
      stepSize: Double,
      metric: Metric,
      iterations: Int,
      progress: Progress)(implicit rng: RNG): List[Array[Double]] = {
    var nextOutputTime = System.nanoTime()
    val buf = new ListBuffer[Array[Double]]
    var i = 0

    while (i < iterations) {
      sampler.run(params, lf, stepSize, metric)
      val output = new Array[Double](lf.nVars)
      lf.variables(params, output)
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
