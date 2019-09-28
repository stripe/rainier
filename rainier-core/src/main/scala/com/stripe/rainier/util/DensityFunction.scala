package com.stripe.rainier.util

trait DensityFunction {
  def nVars: Int
  def update(vars: Array[Double]): Unit
  def density: Double
  def gradient(index: Int): Double
}
