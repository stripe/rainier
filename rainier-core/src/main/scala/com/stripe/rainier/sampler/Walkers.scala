package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer

import Log._
import java.util.concurrent.TimeUnit._

final case class Walkers(walkers: Int) extends Sampler {
  def sample(density: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    FINE.log("Initializing %d walkers", walkers)
    val initial = WalkersChain(density, walkers)

    FINE.log("Starting %d warmup iterations", warmupIterations)
    val warmedUp =
      0.until(warmupIterations)
        .foldLeft(initial) {
          case (chain, i) =>
            FINE.atMostEvery(1, SECONDS).log("Iteration %d", i)
            chain.next
        }

    val buf = new ListBuffer[Array[Double]]
    var i = 0
    var chain = warmedUp

    FINE.log("Starting %d iterations", iterations)
    while (i < iterations) {
      FINE.atMostEvery(1, SECONDS).log("Iteration %d", i)
      chain = chain.next
      //keep every nth state of each walker, vs every nth walker
      if ((i / walkers) % keepEvery == 0)
        buf += chain.variables
      i += 1
    }

    FINE.log("Finished sampling")
    buf.toList
  }
}
