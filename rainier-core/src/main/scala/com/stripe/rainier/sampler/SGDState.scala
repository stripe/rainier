package com.stripe.rainier.sampler

import com.stripe.rainier.compute.Context

private[sampler] class SGDState(context: Context)(implicit rng: RNG) {
  val cf = context.compiler
    .compileUnsafe(context.variables ++ context.placeholders,
                   context.density :: context.gradient)
  var inputBuf = new Array[Double](cf.numInputs)
  val outputBuf = new Array[Double](cf.numOutputs)
  val globalsBuf = new Array[Double](cf.numGlobals)

  0.until(context.variables.size).foreach { i =>
    inputBuf(i) = rng.standardNormal
  }

  def step(stepSize: Double, batch: Array[Double]): Double = {
    val nVars = context.variables.size
    var i = 0
    while (i < batch.size) {
      inputBuf(nVars + i) = batch(i)
      i += 1
    }
    cf(inputBuf, globalsBuf, outputBuf)
    i = 1
    while (i < outputBuf.size) {
      inputBuf(i - 1) += (outputBuf(i) * stepSize)
      i += 1
    }
    outputBuf(0)
  }

  def parameters: Array[Double] =
    inputBuf.take(context.variables.size)
}
