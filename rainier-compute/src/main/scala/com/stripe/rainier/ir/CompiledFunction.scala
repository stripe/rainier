package com.stripe.rainier.ir

import Log._

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def output0(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output1(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output2(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output3(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output4(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output5(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output6(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output7(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output8(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
  def output9(inputs: Array[Double],
              globals: Array[Double],
              output: Int): Double
}

object CompiledFunction {
  def apply(inputs: Seq[Param],
            exprs: Seq[(String, Expr)],
            methodSizeLimit: Int,
            classSizeLimit: Int): CompiledFunction = {
    FINE.log(
      "Compiling %d inputs, %d outputs, methodSizeLimit %s, classSizeLimit %d",
      inputs.size,
      exprs.size,
      methodSizeLimit,
      classSizeLimit)

    val outputClassName = ClassGenerator.freshName

    val methodGroups = exprs.map {
      case (name, expr) =>
        val packer = new Packer(methodSizeLimit)

        FINE.log("Packing expression for %s", name)
        val outputRef = packer.pack(expr)
        FINE.log("Packed %s into %d methods", name, packer.methods.size)

        (outputClassName + "$" + name, outputRef, packer.methods)
    }
    val allMeths = methodGroups.flatMap(_._3)

    FINE.log("Scanning var types")
    val varTypes = VarTypes.methods(allMeths.toList)
    FINE.log("Found references for %d symbols", varTypes.numReferences.size)

    FINE.log("Generating method nodes")
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

    FINE.log("Found %d locals and %d globals",
             varTypes.locals.size,
             varTypes.globals.size)

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

    FINE.log("Generating class nodes")
    val ecgs = methodNodes
      .groupBy(_._1)
      .map {
        case (className, nodes) =>
          new ExprClassGenerator(className, nodes.toList.map(_._2))
      }
      .toList

    val parentClassLoader = this.getClass.getClassLoader
    val classLoader = new GeneratedClassLoader(ocg, ecgs, parentClassLoader)
    val bytecodeSize = classLoader.bytecode.map(_.size).sum
    FINE.log("Creating new instance of %s, total bytecode size %d",
             outputClassName,
             bytecodeSize)
    classLoader.newInstance
  }

  def output(cf: CompiledFunction,
             inputs: Array[Double],
             globals: Array[Double],
             index: Int): Double = {
    val i = index % 10
    val j = index / 10
    i match {
      case 0 => cf.output0(inputs, globals, j)
      case 1 => cf.output1(inputs, globals, j)
      case 2 => cf.output2(inputs, globals, j)
      case 3 => cf.output3(inputs, globals, j)
      case 4 => cf.output4(inputs, globals, j)
      case 5 => cf.output5(inputs, globals, j)
      case 6 => cf.output6(inputs, globals, j)
      case 7 => cf.output7(inputs, globals, j)
      case 8 => cf.output8(inputs, globals, j)
      case 9 => cf.output9(inputs, globals, j)
    }
  }

}
