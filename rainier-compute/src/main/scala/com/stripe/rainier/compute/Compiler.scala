package com.stripe.rainier.compute

import com.stripe.rainier.ir

final case class Compiler(methodSizeLimit: Int, classSizeLimit: Int) {
  def compile(parameters: Seq[Parameter],
              real: Real): Array[Double] => Double = {
    val cf = compile(parameters.map(_.param), List(("base", real)))
    return { array =>
      val globalBuf = new Array[Double](cf.numGlobals)
      ir.CompiledFunction.output(cf, array, globalBuf, 0)
    }
  }
  def compileTargets(group: TargetGroup): ir.DataFunction = {
    val cf = compile(group.inputs, group.outputs)
    ir.DataFunction(cf,
                    group.parameters.size,
                    group.parameters.size + 1,
                    group.data)
  }

  def compile(inputs: Seq[ir.Param],
              outputs: Seq[(String, Real)]): ir.CompiledFunction = {
    val translator = new Translator
    val exprs = outputs.map {
      case (s, r) =>
        s -> translator.toExpr(r)
    }
    ir.CompiledFunction(inputs, exprs, methodSizeLimit, classSizeLimit)
  }
}

object Compiler {
  def default: Compiler = Compiler(200, 100)
}
