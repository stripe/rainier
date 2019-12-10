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
                        data: Array[Array[Array[Double]]]) {
  val numInputs: Int = cf.numInputs
  val numGlobals: Int = cf.numGlobals
  val numParamInputs = parameters.size
  val numOutputs = parameters.size + 1

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

object DataFunction {
  def apply(targets: List[Real], compiler: Compiler = Compiler.default): DataFunction = {
    val (paramSet, placeholders, dataList, base, batch) =
      targets.foldLeft((Set.empty[Parameter], List.empty[Placeholder], List.empty[Array[Array[Double]]], Real.zero, List.empty[Real])) {
        case ((paramAcc, phAcc, dataAcc, baseAcc, batchAcc), target) =>
          val variables = RealOps.variables(target)
          val targetParams = variables.collect{case x:Parameter => x}
          val targetPh = variables.collect{case x:Placeholder => x}.toList
          if(targetPh.isEmpty) {
            (targetParams ++ paramAcc, phAcc, dataAcc, baseAcc + target, batchAcc) 
          } else {
            (targetParams ++ paramAcc,
            targetPh ++ phAcc,
            targetPh.map(_.values).toArray :: dataAcc,
            baseAcc,
            target :: batchAcc)
          }
      }

    val parameters = paramSet.toList
    val data = dataList.toArray
    val batchOutputs = batch.zipWithIndex.flatMap {
      case (o, i) =>
        Compiler.withGradient(s"target${i}", o, parameters)
    }

    val cf = compiler.compile(
      parameters ++ placeholders,
      Compiler.withGradient("base", base, parameters) ++ batchOutputs)

      DataFunction(cf, parameters, data)
  }
}