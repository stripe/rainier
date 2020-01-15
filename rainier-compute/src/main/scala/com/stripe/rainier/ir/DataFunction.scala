package com.stripe.rainier.ir

/*
Input layout:
- numParamInputs param inputs
- for 0 <= i < data.size:
   - data[i].size inputs

Output layout:
- for 0 <= i < data.size:
   - numOutputs outputs
 */
case class DataFunction(cf: CompiledFunction,
                        numParamInputs: Int,
                        numOutputs: Int,
                        data: Array[Array[Array[Double]]]) {
  val numInputs: Int = cf.numInputs
  val numGlobals: Int = cf.numGlobals

  private val inputStartIndices =
    data.map(_.size).scanLeft(numParamInputs) {
      case (x, sz) => x + sz
    }
  require(inputStartIndices(data.size) == cf.numInputs)

  private val outputStartIndices =
    data.scanLeft(0) {
      case (x, _) => x + numOutputs
    }
  require(outputStartIndices(data.size) == cf.numOutputs)

  def apply(inputs: Array[Double],
            globals: Array[Double],
            outputs: Array[Double]): Unit = {
    var k = 0
    while (k < outputs.size) {
      outputs(k) = 0.0
      k += 1
    }

    var i = 0
    while (i < data.size) {
      compute(inputs, globals, outputs, i)
      i += 1
    }
  }

  private def compute(inputs: Array[Double],
                      globals: Array[Double],
                      outputs: Array[Double],
                      i: Int): Unit = {
    val inputStartIndex = inputStartIndices(i)
    val outputStartIndex = outputStartIndices(i)
    val d = data(i)
    if (d.size > 0) {
      var k = 0
      val n = d(0).size
      while (k < n) {
        var j = 0
        while (j < d.size) {
          inputs(inputStartIndex + j) = d(j)(k)
          j += 1
        }
        var o = 0
        while (o < numOutputs) {
          outputs(o) += CompiledFunction.output(cf,
                                                inputs,
                                                globals,
                                                outputStartIndex + o)
          o += 1
        }
        k += 1
      }
    } else {
      var o = 0
      while (o < numOutputs) {
        outputs(o) += CompiledFunction.output(cf,
                                              inputs,
                                              globals,
                                              outputStartIndex + o)
        o += 1
      }
    }
  }
}
