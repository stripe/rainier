package com.stripe.rainier.sampler

trait Progress {
  def start(state: SamplerState): Unit
  def refresh(state: SamplerState): Unit
  def finish(state: SamplerState): Unit
  def outputEverySeconds: Double
}

object SilentProgress extends Progress {
  def start(state: SamplerState): Unit = ()
  def refresh(state: SamplerState): Unit = ()
  def finish(state: SamplerState): Unit = ()
  val outputEverySeconds = 1e100
}
