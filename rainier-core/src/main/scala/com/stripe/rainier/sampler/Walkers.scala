package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer

final case class Walkers(walkers: Int) extends Sampler {
  def sample(densityFunction: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val initial = WalkersChain(densityFunction, walkers)
    val warmedUp =
      1.to(warmupIterations)
        .foldLeft(initial) {
          case (chain, _) =>
            chain.next
        }

    val buf = new ListBuffer[Array[Double]]
    var i = 0
    var chain = warmedUp
    while (i < iterations) {
      chain = chain.next
      //keep every nth state of each walker, vs every nth walker
      if ((i / walkers) % keepEvery == 0)
        buf += chain.variables
      i += 1
    }
    buf.toList
  }
}
