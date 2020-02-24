package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer

object Driver {
  def sample(
      chain: Int,
      sampler: Sampler,
      density: DensityFunction,
      progress: Progress)(implicit rng: RNG): List[Array[Double]] = {
        val lf = new LeapFrog(density)
        progress.start(chain, lf.stats)

        val tuning = warmup(chain, sampler, lf, progress)
        val samples = collectSamples(chain, sampler, lf, progress)(tuning)

        progress.finish(chain, lf.stats)
        samples
  }

  private def warmup(chain: Int, sampler: Sampler, lf: LeapFrog, progress: Progress)(implicit rng: RNG): sampler.S = {
      var i = 0
      var nextOutputTime = System.nanoTime()

      var ws = sampler.initialWindowSize

      var foreground = sampler.initialize(lf)
      sampler.prepareForeground(foreground)
      var background = sampler.initialize(lf)
      sampler.prepareBackground(background)

      val it = sampler.warmupIterations
      while(i < it) {
        var w = 0
        while(w < ws && i < it) {
          sampler.warmup(foreground, background, lf)
          w += 1
          i += 1

          if(System.nanoTime() > nextOutputTime) {
            progress.refresh(chain, lf.stats)
            nextOutputTime = System.nanoTime() + (progress.outputEverySeconds * 1e9).toLong
          }
        } 

        val tmp = background
        background = foreground
        foreground = tmp
        sampler.prepareForeground(foreground)
        sampler.prepareBackground(background)

        ws = Math.ceil(ws * sampler.windowExpansion).toInt
      }
      foreground
  }

  private def collectSamples(chain: Int, sampler: Sampler, lf: LeapFrog, progress: Progress)(tuning: sampler.S)(implicit rng: RNG): List[Array[Double]] = {
    var nextOutputTime = System.nanoTime()
    val buf = new ListBuffer[Array[Double]]
    var i = 0
    val it = sampler.iterations
    while (i < it) {
      sampler.run(tuning, lf)
      val output = new Array[Double](lf.nVars)
      lf.variables(output)
      buf += output
      i += 1

      if(System.nanoTime() > nextOutputTime) {
        progress.refresh(chain, lf.stats)
        nextOutputTime = System.nanoTime() + (progress.outputEverySeconds * 1e9).toLong
      }
    }
    buf.toList    
  }  
}
