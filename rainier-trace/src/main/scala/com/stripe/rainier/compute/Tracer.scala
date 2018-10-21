package com.stripe.rainier.compute

import com.stripe.rainier.ir._

object Tracer {
  def dump(real: Real,
           methodSizeLimit: Int = 200,
           classSizeLimit: Int = 100): Unit = {
    val translator = new Translator
    val exprs = List(translator.toExpr(real))
    val params = real.variables.map(_.param)
    val bytecode =
      CompiledFunction.bytecode(params, exprs, methodSizeLimit, classSizeLimit)
    CFR.decompile(bytecode).foreach(println)
  }

  def dumpGradient(real: Real,
                   methodSizeLimit: Int = 200,
                   classSizeLimit: Int = 100): Unit = {
    val translator = new Translator
    val variables = real.variables
    val gradient = Gradient.derive(variables, real)
    val exprs = translator.toExpr(real) :: gradient.map { r =>
      translator.toExpr(r)
    }
    val params = variables.map(_.param)
    val bytecode =
      CompiledFunction.bytecode(params, exprs, methodSizeLimit, classSizeLimit)
    CFR.decompile(bytecode).foreach(println)
  }
}
