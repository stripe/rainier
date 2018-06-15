package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class SGD(stepSize: Double) extends Optimizer {
  def optimize(context: Context,
               observations: List[Observations],
               iterations: Int)(implicit rng: RNG): Array[Double] = {
    val placeholders = observations.flatMap(_.variables)
    val state = new SGDState(context, placeholders)

    val batchBuf = new Array[Double](placeholders.size)
    val nBatches = observations.head.numBatches
    var i = 0
    while (i < iterations) {
      var batch = 0
      var err = 0.0
      while (batch < nBatches) {
        fill(batchBuf, observations, batch)
        err -= state.step(stepSize, batchBuf)
        batch += 1
      }

      println(s"Epoch $i: ${err / nBatches}")

      i += 1
    }

    state.parameters
  }

  private def fill(batchBuf: Array[Double],
                   observations: List[Observations],
                   batch: Int): Unit = ???
}
