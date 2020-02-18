package com.stripe.rainier.sampler

case class EmpiricalPathLength(iterations: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    val pathLengths = Vector.fill(iterations) { state.longestBatchStep() }
    state.updatePathLength(Iterator.continually {
      pathLengths(rng.int(iterations))
    })
  }
}
