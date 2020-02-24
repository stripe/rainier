package com.stripe.rainier.sampler

trait Sampler {
  type S
  def iterations: Int
  def warmupIterations: Int
  def initialWindowSize: Int
  def windowExpansion: Double

  def initialize(lf: LeapFrog): S
  def prepareBackground(s: S)(implicit rng: RNG): S
  def prepareForeground(s: S)(implicit rng: RNG): S

  def warmup(fg: S, bg: S, lf: LeapFrog)(implicit rng: RNG): Unit
  def run(fg: S, lf: LeapFrog)(implicit rng: RNG): Unit
}

object Sampler {
  val default: Sampler = HMC(10000, 1000, 10)
}