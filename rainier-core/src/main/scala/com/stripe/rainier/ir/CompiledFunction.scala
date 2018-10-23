package com.stripe.rainier.ir

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def output(inputs: Array[Double], globals: Array[Double], output: Int): Double
}

object CompiledFunction {
  def apply(inputs: Seq[Parameter],
            exprs: Seq[(String, Expr)],
            methodSizeLimit: Int,
            classSizeLimit: Int): CompiledFunction = {
    val outputClassName = ClassGenerator.freshName
    val methodGroups = exprs.map {
      case (name, expr) =>
        val packer = new Packer(methodSizeLimit)
        val outputRef = packer.pack(expr)
        (outputClassName + "$" + name, outputRef, packer.methods)
    }
    val allMeths = methodGroups.flatMap(_._3)
    val varTypes = VarTypes.methods(allMeths.toList)

    val methodNodes = methodGroups.flatMap {
      case (classPrefix, _, methods) =>
        methods.map { meth =>
          val mg = new ExprMethodGenerator(meth,
                                           inputs,
                                           varTypes,
                                           classPrefix,
                                           classSizeLimit)
          mg.className -> mg.methodNode
        }
    }

    val numInputs = inputs.size
    val numGlobals = varTypes.globals.size
    val numOutputs = methodGroups.size

    val outputIDs = methodGroups.map {
      case (classPrefix, outputRef, _) =>
        (classPrefix, outputRef.sym.id)
    }

    val ocg = new OutputClassGenerator(outputClassName,
                                       classSizeLimit,
                                       outputIDs,
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
