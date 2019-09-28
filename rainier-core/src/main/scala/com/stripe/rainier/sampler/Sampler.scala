package com.stripe.rainier.sampler

import scala.annotation.tailrec

trait Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG): List[Array[Double]]
}

object Sampler {
  object Default {
    val sampler: Sampler = HMC(5)
    val iterations: Int = 10000
    val warmupIterations: Int = 1000
  }

  def diagnostics(chains: List[List[Array[Double]]]): List[Diagnostics] = {
    val nParams = chains.head.head.size
    0.until(nParams).toList.map { i =>
      val traces = chains.map { c =>
        c.map { a =>
          a(i)
        }.toArray
      }
      Diagnostics(traces)
    }
  }
}
