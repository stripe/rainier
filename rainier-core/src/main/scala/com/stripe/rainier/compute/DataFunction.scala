package com.stripe.rainier.compute

import com.stripe.rainier.ir.CompiledFunction

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
   - for batchBits >= j >= 0:
      - numOutputs batch outputs
 */
case class DataFunction(cf: CompiledFunction,
                        parameters: List[Parameter],
                        numOutputs: Int,
                        data: Array[Array[Array[Double]]]) {
  val numInputs: Int = cf.numInputs
  val numGlobals: Int = cf.numGlobals
  val numParamInputs = parameters.size

  private val inputStartIndices =
    data.map(_.size).scanLeft(numParamInputs) {
      case (x, sz) => x + sz
    }

  require(inputStartIndices(data.size) == cf.numInputs)

  private val outputStartIndices =
    data.scanLeft(numOutputs) {
      case (x, _) => x + numOutputs
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

  //TODO: from here down
  private def computeBatch(inputs: Array[Double],
                           globals: Array[Double],
                           outputs: Array[Double],
                           d: Array[Array[Double]],
                           n: Int,
                           inputStartIndex: Int,
                           outputStartIndex: Int): Unit = ()

}
