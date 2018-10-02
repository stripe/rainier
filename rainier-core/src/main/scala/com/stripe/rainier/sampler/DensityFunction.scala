package com.stripe.rainier.sampler

trait DensityFunction {
  def nVars: Int
  def update(vars: Array[Double]): Unit
  def density: Double
  def gradient(index: Int): Double
  def gradientVector: Array[Double] = {
    val grad = new Array[Double](nVars)
    var i = 0
    while (i < nVars) {
      grad(i) = gradient(i)
      i += 1
    }
    grad
  }
}
