package com.stripe.rainier.compute

import com.stripe.rainier.ir.CompiledFunction
import com.stripe.rainier.sampler.DensityFunction

class CompiledModel(val parameters: List[Parameter],
                    cf: CompiledFunction,
                    targets: Array[CompiledBatch]) { self =>
  val numOutputs = cf.numOutputs

  def update(inputs: Array[Double],
             globals: Array[Double],
             outputs: Array[Double]): Unit = {
    var o = 0
    while (o < numOutputs) {
      outputs(o) = cf.output(inputs, globals, o)
      o += 1
    }

    var i = 0
    while (i < targets.size) {
      targets(i).update(inputs, globals, outputs)
      i += 1
    }
  }

  def density(): DensityFunction =
    new DensityFunction {
      val nVars = parameters.size
      val inputs = new Array[Double](cf.numInputs)
      val globals = new Array[Double](cf.numGlobals)
      val outputs = new Array[Double](cf.numOutputs)
      def update(vars: Array[Double]): Unit = {
        System.arraycopy(vars, 0, inputs, 0, nVars)
        self.update(inputs, globals, outputs)
      }
      def density = outputs(0)
      def gradient(index: Int) = outputs(index + 1)
    }
}

case class CompiledBatch(cf: CompiledFunction,
                         inputStartIndex: Int,
                         outputStartIndex: Int,
                         data: Array[Array[Double]]) {
  val numRows = data(0).size

  def update(inputs: Array[Double],
             globals: Array[Double],
             outputs: Array[Double]): Unit = {
    var j = 0
    while (j < numRows) {
      var i = 0
      while (i < data.size) {
        inputs(inputStartIndex + i) = data(i)(j)
        i += 1
      }
      var o = 0
      while (o < outputs.size) {
        outputs(o) += cf.output(inputs, globals, outputStartIndex + o)
        o += 1
      }
    }
    j += 1
  }
}
