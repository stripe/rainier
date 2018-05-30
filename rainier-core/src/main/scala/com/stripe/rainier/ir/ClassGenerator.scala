package com.stripe.rainier.ir

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def apply(inputs: Array[Double],
            globals: Array[Double],
            outputs: Array[Double]): Unit
}

object ClassGenerator {
  def generate(inputs: Seq[Parameter],
               irs: Seq[IR],
               methodSizeLimit: Int,
               writeToTmpFile: Boolean): CompiledFunction = {
    val className = CompiledClass.freshName
    val packer = new Packer(methodSizeLimit)
    val outputMeths = irs.map { ir =>
      packer.pack(ir)
    }
    val allMeths = packer.methods

    val varTypes = VarTypes.methods(allMeths.toList)
    val methodNodes = allMeths.map { meth =>
      val mg = new ExprMethodGenerator(meth, inputs, varTypes, className)
      mg.methodNode
    }
    val amg = new ApplyMethodGenerator(className, outputMeths.map(_.sym.id))

    val numInputs = inputs.size
    val numGlobals = varTypes.globals.size
    val numOutputs = outputMeths.size

    val cc = new CompiledClass(className,
                               amg.methodNode :: methodNodes.toList,
                               numInputs,
                               numGlobals,
                               numOutputs)
    if (writeToTmpFile)
      cc.writeToTmpFile
    cc.instance
  }
}
