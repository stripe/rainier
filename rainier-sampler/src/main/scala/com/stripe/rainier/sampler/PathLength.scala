package com.stripe.rainier.sampler

trait PathLength {
  def baseSteps: Int
  def nSteps(state: SamplerState)(implicit rng: RNG): Int
  def stepSize(nSteps: Int, state: SamplerState)(implicit rng: RNG): Double
  def findUTurnEvery: Int
}

case class FixedStepCount(n: Int) extends PathLength {
  def baseSteps = n
  def nSteps(state: SamplerState)(implicit rng: RNG) = n
  def stepSize(nSteps: Int, state: SamplerState)(implicit rng: RNG) =
    state.stepSize
  def findUTurnEvery = 1000000
}

case class Jitter(orig: PathLength) extends PathLength {
  def baseSteps = orig.baseSteps
  def nSteps(state: SamplerState)(implicit rng: RNG) =
    orig.nSteps(state)
  def stepSize(nSteps: Int, state: SamplerState)(implicit rng: RNG) = {
    val base = orig.stepSize(nSteps, state)
    base * rng.standardUniform
  }
  def findUTurnEvery = orig.findUTurnEvery
}
