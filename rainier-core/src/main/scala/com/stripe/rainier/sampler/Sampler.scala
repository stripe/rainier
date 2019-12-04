package com.stripe.rainier.sampler

trait Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG): List[List[Array[Double]]]

  def chains(nChains: Int, parallel: Boolean = true): Sampler = 
    MultiChainSampler(this, nChains, parallel)

  def keepEvery(k: Int): Sampler = ThinningSampler(this, k)
}

case class MultiChainSampler(original: Sampler, nChains: Int, parallel: Boolean) extends Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG) = ???
}

case class ThinningSampler(original: Sampler, keepEvery: Int) extends Sampler {
  def sample(density: DensityFunction)(implicit rng: RNG) = ???
}