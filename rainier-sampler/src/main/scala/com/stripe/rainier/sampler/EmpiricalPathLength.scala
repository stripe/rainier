package com.stripe.rainier.sampler

case class EmpiricalPathLength(iterations: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    state.startPhase("Gathering path lengths", iterations)
    val pathLengths = Vector.fill(iterations) { state.longestBatchStep() }
    state.updatePathLength(Iterator.continually {
      pathLengths(rng.int(iterations))
    })
  }
}
