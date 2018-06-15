package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

trait Optimizer {
  def optimize(context: Context,
               observations: List[Observations],
               iterations: Int)(implicit rng: RNG): Array[Double]
}

class Observations(val columns: Array[Array[Double]], numVariables: Int) {
  val numBatches = columns.head.size
  val variables = columns.map { _ =>
    new Variable
  }
  def variableBatch = variables.grouped(numVariables).toList
}
