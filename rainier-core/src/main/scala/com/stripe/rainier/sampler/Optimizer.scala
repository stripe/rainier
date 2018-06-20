package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

trait Optimizer {
  def optimize(context: Context,
               batches: Array[Array[Double]],
               iterations: Int): Array[Double]
}
