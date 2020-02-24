package com.stripe.rainier.sampler

case class HMC(warmupIterations: Int, iterations: Int, nSteps: Int) extends Sampler {
  type S
  val initialWindowSize = warmupIterations
  val windowExpansion = 1.0

  def initialize(lf: LeapFrog): S = ???
  def prepareBackground(s: S)(implicit rng: RNG): S = ???
  def prepareForeground(s: S)(implicit rng: RNG): S = ???

  def warmup(fg: S, bg: S, lf: LeapFrog)(implicit rng: RNG): Unit = ???
  def run(fg: S, lf: LeapFrog)(implicit rng: RNG): Unit = ???
}