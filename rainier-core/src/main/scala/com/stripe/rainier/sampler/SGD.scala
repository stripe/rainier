package com.stripe.rainier.sampler

import com.stripe.rainier.compute.Context

final case class SGD(stepSize: Double) extends Optimizer {
  def optimize(context: Context,
               batches: Array[Array[Double]],
               iterations: Int)(implicit rng: RNG): Array[Double] = {
    val state = new SGDState(context)

    val batchBuf = new Array[Double](batches.size)
    val nBatches = batches.head.size
    var i = 0
    while (i < iterations) {
      var batch = 0
      var err = 0.0
      while (batch < nBatches) {
        var col = 0
        while (col < batchBuf.size) {
          batchBuf(col) = batches(col)(batch)
          col += 1
        }
        err += state.step(stepSize, batchBuf)
        batch += 1
      }

      println(s"Epoch $i: ${err / nBatches}")

      i += 1
    }

    state.parameters
  }
}
