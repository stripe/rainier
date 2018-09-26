package com.stripe.rainier.compute

import com.stripe.rainier.ir

trait Compiler {
  def compile(inputs: Seq[Variable], output: Real): Array[Double] => Double =
    compile(inputs, List(output)).andThen { array =>
      array(0)
    }

  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val cf = compileUnsafe(inputs, outputs)
    val fn = { in: Array[Double] =>
      val globals = new Array[Double](cf.numGlobals)
      val out = new Array[Double](cf.numOutputs)
      cf(in, globals, out)
      out
    }
    fn
  }

  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): ir.CompiledFunction
}

final case class IRCompiler(methodSizeLimit: Int, classSizeLimit: Int)
    extends Compiler {
  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): ir.CompiledFunction = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val irs = outputs.map { r =>
      translator.toIR(r)
    }
    ir.CompiledFunction(params, irs, methodSizeLimit, classSizeLimit)
  }
}
