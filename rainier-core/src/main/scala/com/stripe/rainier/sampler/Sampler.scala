package com.stripe.rainier.sampler

trait Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG): List[Array[Double]]
}

object Sampler {
  val default = EHMC(20, 1000, 10000, 10000)
}
