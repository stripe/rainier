package com.stripe.rainier.sampler

case class FixedPathLength(n: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit =
    state.updatePathLength(Iterator.continually(n))
}
