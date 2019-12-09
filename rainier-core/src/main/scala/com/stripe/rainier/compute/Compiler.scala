package com.stripe.rainier.compute

import com.stripe.rainier.ir.CompiledFunction

final case class Compiler(methodSizeLimit: Int, classSizeLimit: Int) {
  def compileTargets(targets: List[Real], gradient: Boolean): DataFunction = {
    val parameters: List[Parameter] = ???
    val gradParams: List[Parameter] = ???
    val placeholders: List[Placeholder] = ???
    val base: Real = ???
    val batchOutputs: Seq[(String, Real)] = ???
    val data: Array[Array[Array[Double]]] = ???

    val cf = compile(
      parameters ++ placeholders,
      Compiler.withGradient("base", base, gradParams) ++ batchOutputs)
    val numOutputs =
      if (gradient)
        parameters.size + 1
      else
        1
    DataFunction(cf, parameters, numOutputs, data)
  }

  def compile(inputs: Seq[Variable],
              outputs: Seq[(String, Real)]): CompiledFunction = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val exprs = outputs.map {
      case (s, r) =>
        s -> translator.toExpr(r)
    }
    CompiledFunction(params, exprs, methodSizeLimit, classSizeLimit)
  }
}

object Compiler {
  def default: Compiler = Compiler(200, 100)

  def withGradient(name: String,
                   real: Real,
                   parameters: List[Parameter]): List[(String, Real)] =
    if (parameters.isEmpty)
      List((name, real))
    else
      (name, real) :: Gradient
        .derive(parameters, real)
        .zipWithIndex
        .map {
          case (g, i) =>
            (s"${name}_grad${i}", g)
        }
}
