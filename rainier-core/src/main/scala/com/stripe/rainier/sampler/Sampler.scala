package com.stripe.rainier.sampler

trait Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG): List[Array[Double]]
}