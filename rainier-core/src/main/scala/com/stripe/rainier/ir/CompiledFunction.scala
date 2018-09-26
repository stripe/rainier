package com.stripe.rainier.ir

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def output(inputs: Array[Double], globals: Array[Double], output: Int): Double

  def apply(inputs: Array[Double],
            globals: Array[Double],
            outputs: Array[Double]): Unit = {
    var i = 0
    while (i < numOutputs) {
      outputs(i) = output(inputs, globals, i)
      i += 1
    }
  }
}

object CompiledFunction {
  def apply(inputs: Seq[Parameter],
            irs: Seq[IR],
            methodSizeLimit: Int,
            classSizeLimit: Int): CompiledFunction = {
    val classPrefix = ClassGenerator.freshName
    val packer = new Packer(methodSizeLimit)
    val outputMeths = irs.map { ir =>
      packer.pack(ir)
    }
    val allMeths = packer.methods
    val varTypes = VarTypes.methods(allMeths.toList)

    val methodNodes = allMeths.map { meth =>
      val mg = new ExprMethodGenerator(meth,
                                       inputs,
                                       varTypes,
                                       classPrefix,
                                       classSizeLimit)
      mg.className -> mg.methodNode
    }

    val numInputs = inputs.size
    val numGlobals = varTypes.globals.size
    val numOutputs = outputMeths.size

    val acg = new ApplyClassGenerator(classPrefix,
                                      classSizeLimit,
                                      outputMeths.map(_.sym.id),
                                      numInputs,
                                      numGlobals,
                                      numOutputs)

    val ecgs = methodNodes
      .groupBy(_._1)
      .map {
        case (className, nodes) =>
          new ExprClassGenerator(className, nodes.toList.map(_._2))
      }
      .toList

    val parentClassLoader = this.getClass.getClassLoader
    val classLoader =
      new GeneratedClassLoader(acg, ecgs, parentClassLoader)

    classLoader.newInstance
  }
}
