package com.stripe.rainier.sampler

trait DensityFunction {
  def nVars: Int
  def density(params: Array[Double]): Double
  def gradient(params: Array[Double]): Array[Double]
}
