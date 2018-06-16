package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

trait Optimizer {
  def optimize(context: Context, batches: Batches, iterations: Int)(
      implicit rng: RNG): Array[Double]
}

class Batches(val columns: Array[(Variable, Array[Double])]) {
  val numBatches = columns.head._2.size
}
