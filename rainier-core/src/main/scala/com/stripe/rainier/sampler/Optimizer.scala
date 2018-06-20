package com.stripe.rainier.sampler

import com.stripe.rainier.compute.Context

trait Optimizer {
  def optimize(context: Context,
               batches: Array[Array[Double]],
               iterations: Int)(implicit rng: RNG): Array[Double]
}
