package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class SGD(stepSize: Double, data: Map[Variable, Array[Double]])
    extends Sampler {
  def sample(context: Context,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val columns = data.toArray
    val state = new SGDState(context, data.map(_._1).toList)

    val nBatches = data.toList.head._2.size
    val batchBuf = new Array[Double](data.size)
    var results = List.empty[Array[Double]]

    var i = 0
    while (i < warmupIterations + iterations) {
      var batch = 0
      var err = 0.0
      while (batch < nBatches) {
        var column = 0
        while (column < columns.size) {
          batchBuf(column) = columns(column)._2(batch)
          column += 1
        }
        val batchErr = state.step(stepSize, batchBuf)
        err += batchErr
        batch += 1
      }

      println(s"Epoch $i: ${err / nBatches}")
      if (i >= warmupIterations)
        results = state.parameters :: results

      i += 1
    }

    results.reverse
  }
}

private class SGDState(context: Context, placeholders: List[Variable])(
    implicit rng: RNG) {
  val variables = (context.variables.toSet -- placeholders.toSet).toList
  val grad = Gradient
    .derive(variables ++ placeholders, context.density)
    .take(variables.size)
    .toList
  val cf = context.compiler
    .compileUnsafe(variables ++ placeholders, context.density :: grad)
  var inputBuf = new Array[Double](cf.numInputs)
  val outputBuf = new Array[Double](cf.numOutputs)
  val globalsBuf = new Array[Double](cf.numGlobals)

  0.until(variables.size).foreach { i =>
    inputBuf(i) = rng.standardNormal
  }

  def step(stepSize: Double, batch: Array[Double]): Double = {
    val nVars = variables.size
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
    context.variables.map { v =>
      inputBuf((variables ++ placeholders).indexOf(v))
    }.toArray
}
