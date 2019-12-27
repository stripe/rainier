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

  def compileTargets(targets: TargetGroup,
                     gradient: Boolean): ir.DataFunction = {
    val data = targets.batched.map { target =>
      target.columns.map { v =>
        v.values.map(_.toDouble).toArray
      }.toArray
    }.toArray

    val gradVars = if (gradient) targets.parameters else Nil
    val (columns, batchOutputs) =
      targets.batched.zipWithIndex
        .foldLeft((List.empty[Column], List.empty[(String, Real)])) {
          case ((ins, outs), (target, i)) =>
            val (newIns, newOuts) = target.batched
            val newOutsWithGradient =
              newOuts.zipWithIndex.flatMap {
                case (o, j) =>
                  Compiler.withGradient(s"target${i}_bit${j}", o, gradVars)
              }
            (ins ++ newIns, outs ++ newOutsWithGradient)
        }

    val inputs = targets.parameters.map(_.param) ++ columns.map(_.param)
    val cf = compile(
      inputs,
      Compiler.withGradient("base", targets.base, gradVars) ++ batchOutputs)
    val numOutputs =
      if (gradient)
        targets.parameters.size + 1
      else
        1
    ir.DataFunction(cf, targets.parameters.size, numOutputs, data)
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
