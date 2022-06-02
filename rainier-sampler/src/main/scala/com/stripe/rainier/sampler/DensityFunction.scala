package com.stripe.rainier.sampler

import com.stripe.rainier.RNG

trait DensityFunction {
  def nVars: Int
  def update(rng: RNG, vars: Array[Double]): Unit
  def density: Double
  def gradient(index: Int): Double
}
