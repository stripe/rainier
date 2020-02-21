package com.stripe.rainier.sampler

trait PathLength {
  def baseSteps: Int
  def nSteps(state: SamplerState): Int
  def stepSize(state: SamplerState): Double
  def findUTurnEvery: Int
}

case class FixedStepCount(n: Int) extends PathLength {
  def baseSteps = n
  def nSteps(state: SamplerState) = n
  def stepSize(state: SamplerState) = state.stepSize
  def findUTurnEvery = 1000000
}
