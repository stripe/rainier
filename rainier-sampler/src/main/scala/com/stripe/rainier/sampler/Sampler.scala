package com.stripe.rainier.sampler

trait Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG): List[Array[Double]]
}

object Sampler {
  val default =
    EHMC(warmupIterations = 10000, iterations = 10000, l0 = 10, k = 1000)
}
