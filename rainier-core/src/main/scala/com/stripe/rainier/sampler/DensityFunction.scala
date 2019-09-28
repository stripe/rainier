package com.stripe.rainier.sampler

trait DensityFunction {
  def nVars: Int
  def update(vars: Array[Double]): Unit
  def density: Double
  def gradient(index: Int): Double
}
