package com.stripe.rainier.ir

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def apply(inputs: Array[Double],
            globals: Array[Double],
            outputs: Array[Double]): Unit
}

object CompiledFunction {
  def apply(inputs: Seq[Parameter],
            irs: Seq[IR],
            methodSizeLimit: Int,
            classSizeLimit: Int,
            writeToTmpFiles: Boolean): CompiledFunction = {
    val classPrefix = ClassGenerator.freshName
    val packer = new Packer(methodSizeLimit)
    val outputMeths = irs.map { ir =>
      packer.pack(ir)
    }
    val allMeths = packer.methods
    val varTypes = VarTypes.methods(allMeths.toList)

    val numInputs = inputs.size
    val numGlobals = varTypes.globals.size
    val numOutputs = outputMeths.size

    val acg = new ApplyClassGenerator(classPrefix,
                                      classSizeLimit,
                                      outputMeths.map(_.sym.id),
                                      numInputs,
                                      numGlobals,
                                      numOutputs)

    val methodNodes = allMeths.map { meth =>
      val mg = new ExprMethodGenerator(meth,
                                       inputs,
                                       varTypes,
                                       classPrefix,
                                       classSizeLimit)
      mg.className -> mg.methodNode
    }

    val ecgs = methodNodes
      .groupBy(_._1)
      .map {
        case (className, nodes) =>
          new ExprClassGenerator(className, nodes.toList.map(_._2))
      }
      .toList

    if (writeToTmpFiles) {
      acg.writeToTmpFile
      ecgs.foreach(_.writeToTmpFile)
    }

    val parentClassLoader = this.getClass.getClassLoader
    val classLoader =
      new GeneratedClassLoader(acg, ecgs, parentClassLoader)

    classLoader.newInstance
  }
}
