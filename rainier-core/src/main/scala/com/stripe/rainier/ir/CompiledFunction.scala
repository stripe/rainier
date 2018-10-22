package com.stripe.rainier.ir

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def output(inputs: Array[Double], globals: Array[Double], output: Int): Double
}

object CompiledFunction {
  def apply(inputs: Seq[Parameter],
            exprs: Seq[Expr],
            methodSizeLimit: Int,
            classSizeLimit: Int): CompiledFunction = {
    val classPrefix = ClassGenerator.freshName
    val packer = new Packer(methodSizeLimit)
    val outputMeths = exprs.map { expr =>
      packer.pack(expr)
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

    val ocg = new OutputClassGenerator(classPrefix,
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
    val classLoader = new GeneratedClassLoader(ocg, ecgs, parentClassLoader)
    classLoader.newInstance
  }

  def run(cf: CompiledFunction,
          inputs: Array[Double],
          globals: Array[Double],
          outputs: Array[Double]): Unit = {
    var i = 0
    val n = cf.numOutputs
    while (i < n) {
      outputs(i) = cf.output(inputs, globals, i)
      i += 1
    }
  }
}
