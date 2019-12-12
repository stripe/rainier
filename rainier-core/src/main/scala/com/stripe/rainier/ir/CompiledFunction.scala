package com.stripe.rainier.ir

import Log._

trait CompiledFunction {
  def numInputs: Int
  def numGlobals: Int
  def numOutputs: Int
  def output(inputs: Array[Double], globals: Array[Double], output: Int): Double
}

object CompiledFunction {
  val MethodSizeLimit = 200
  val ClassSizeLimit = 100

  def apply(inputs: Seq[Parameter],
            exprs: Seq[(String, Expr)]): CompiledFunction = {
    FINE.log(
      "Compiling %d inputs, %d outputs, methodSizeLimit %s, classSizeLimit %d",
      inputs.size,
      exprs.size,
      MethodSizeLimit,
      ClassSizeLimit)

    val outputClassName = ClassGenerator.freshName

    val methodGroups = exprs.map {
      case (name, expr) =>
        val packer = new Packer(MethodSizeLimit)

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
                                           ClassSizeLimit)
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
                                       ClassSizeLimit,
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
