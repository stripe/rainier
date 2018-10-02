package com.stripe.rainier.ir

import scala.annotation.tailrec

/*
Input layout:
- numParamInputs param inputs
- for 0 <= i < data.size:
   - data[i].size first-run inputs
   - for 0 <= j <= batchBits:
     - for 0 <= k < data[i].size:
        - 2^j batch inputs

Output layout:
- numOutputs data-less outputs
- for 0 <= i < data.size:
   - numOutputs first-run outputs
   - for 0 <= j <= batchBits:
      - numOutputs batch outputs
*/
case class DataFunction(cf: CompiledFunction,
                        batchBits: Int,
                        numParamInputs: Int,
                        numOutputs: Int,
                        data: Array[Array[Array[Double]]]) {

  val numInputs = cf.numInputs
  val numGlobals = cf.numGlobals

  private val inputMultiplier = (1 << (batchBits + 1))
  private val inputStartIndices =
    data.map(_.size).scanLeft(numParamInputs) {
      case (x, sz) => x + (sz * inputMultiplier)
    }
  require(inputStartIndices(data.size) == cf.numInputs)

  private val outputMultiplier = batchBits + 2
  private val outputStartIndices =
    data.scanLeft(numOutputs) {
      case (x, _) => x + (numOutputs * outputMultiplier)
    }
  require(outputStartIndices(data.size) == cf.numOutputs)

  def apply(inputs: Array[Double],
            globals: Array[Double],
            outputs: Array[Double]): Unit = {
    computeWithoutData(inputs, globals, outputs)
    var i = 0
    while (i < data.size) {
      computeFirstWithData(inputs, globals, outputs, i)
      computeRestWithData(inputs, globals, outputs, i)
      i += 1
    }
  }

  private def computeWithoutData(inputs: Array[Double],
                                 globals: Array[Double],
                                 outputs: Array[Double]): Unit = {

    var o = 0
    while (o < numOutputs) {
      outputs(o) = cf.output(inputs, globals, o)
      o += 1
    }
  }

  private def computeFirstWithData(inputs: Array[Double],
                                   globals: Array[Double],
                                   outputs: Array[Double],
                                   i: Int): Unit = {

    val d = data(i)
    val inputStartIndex = inputStartIndices(i)
    val outputStartIndex = outputStartIndices(i)
    var j = 0
    while (j < d.size) {
      inputs(inputStartIndex + j) = d(j)(0)
      j += 1
    }
    var o = 0
    while (o < numOutputs) {
      outputs(o) += cf.output(inputs, globals, outputStartIndex + o)
      o += 1
    }
  }

  private def computeRestWithData(inputs: Array[Double],
                                  globals: Array[Double],
                                  outputs: Array[Double],
                                  i: Int): Unit =
    computeBatch(inputs,
                 globals,
                 outputs,
                 data(i),
                 data(i)(0).size - 1,
                 inputStartIndices(i),
                 outputStartIndices(i))

  @tailrec
  private def computeBatch(inputs: Array[Double],
                           globals: Array[Double],
                           outputs: Array[Double],
                           d: Array[Array[Double]],
                           n: Int,
                           inputStartIndex: Int,
                           outputStartIndex: Int): Unit =
    if (n > 0) {
      val bit =
        Math.floor(Math.log(n.toDouble) / Math.log(2)).toInt.min(batchBits)
      val newN = initBatchInputs(inputs, inputStartIndex, d, n, bit)
      computeBatchOutputs(inputs, globals, outputs, outputStartIndex, bit)
      computeBatch(inputs,
                   globals,
                   outputs,
                   d,
                   newN,
                   inputStartIndex,
                   outputStartIndex)
    }

  private def initBatchInputs(inputs: Array[Double],
                              startIndex: Int,
                              d: Array[Array[Double]],
                              n: Int,
                              bit: Int): Int = {

    val batchSize = 1 << bit
    val nextN = n - batchSize
    val batchStartIndex = startIndex + (batchSize * d.size)
    var j = 0
    while (j < d.size) {
      val dj = d(j)
      var k = 1
      while (k <= batchSize) {
        val inputIndex = batchStartIndex + (j * batchSize) + k
        inputs(inputIndex) = dj(nextN + k)
        k += 1
      }
      j += 1
    }
    nextN
  }

  private def computeBatchOutputs(inputs: Array[Double],
                                  globals: Array[Double],
                                  outputs: Array[Double],
                                  startIndex: Int,
                                  bit: Int): Unit = {
    var o = 0
    while (o < numOutputs) {
      val outputIndex = startIndex + ((bit + 1) * numOutputs)
      outputs(o) += cf.output(inputs, globals, outputIndex)
      o += 1
    }
  }
}
