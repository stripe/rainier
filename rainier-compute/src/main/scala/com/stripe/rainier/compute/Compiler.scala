package com.stripe.rainier.compute

import com.stripe.rainier.ir

final case class Compiler(methodSizeLimit: Int, classSizeLimit: Int) {
  def compile(parameters: Seq[Parameter],
              real: Real): Array[Double] => Double = {
    val cf = compile(parameters.map(_.param), List(("base", real)))
    return { array =>
      val globalBuf = new Array[Double](cf.numGlobals)
      cf.output(array, globalBuf, 0)
    }
  }

  def compileTargets(group: TargetGroup): ir.DataFunction = {
    val data = group.targets.map { target =>
      target.columns.map { v =>
        v.values.map(_.toDouble).toArray
      }.toArray
    }.toArray

    val columns = group.targets.flatMap(_.columns)
    val inputs = group.parameters.map(_.param) ++ columns.map(_.param)
    val allOutputs = group.targets.zipWithIndex.flatMap {
      case (t, i) =>
        val name = s"target_$i"
        (name -> t.real) ::
          t.gradient.zipWithIndex.map {
          case (g, j) =>
            s"target_${i}_grad_$j" -> g
        }
    }
    val cf = compile(inputs, allOutputs)
    ir.DataFunction(cf, group.parameters.size, group.parameters.size + 1, data)
  }

  def compile(inputs: Seq[ir.Parameter],
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
