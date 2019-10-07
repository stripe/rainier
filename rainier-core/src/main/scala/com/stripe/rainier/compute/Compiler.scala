package com.stripe.rainier.compute

import com.stripe.rainier.ir.{CompiledFunction, DataFunction}

final case class Compiler(methodSizeLimit: Int, classSizeLimit: Int) {
  def compile(variables: Seq[Variable], real: Real): Array[Double] => Double = {
    val cf = compile(variables, List(("base", real)))
    return { array =>
      val globalBuf = new Array[Double](cf.numGlobals)
      cf.output(array, globalBuf, 0)
    }
  }

  def compileTargets(targets: TargetGroup,
                     gradient: Boolean,
                     maxBatchBits: Int): DataFunction = {
    val data = targets.batched.map { target =>
      target.placeholderVariables.map { v =>
        target.placeholders(v)
      }.toArray
    }.toArray

    val batchBits =
      DataFunction
        .logTwo(data.map(_.head.size).reduceOption(_ min _).getOrElse(0))
        .min(maxBatchBits)
        .max(0)

    val gradVars = if (gradient) targets.variables else Nil
    val (batchVariables, batchOutputs) =
      targets.batched.zipWithIndex
        .foldLeft((List.empty[Variable], List.empty[(String, Real)])) {
          case ((ins, outs), (target, i)) =>
            val (newIns, newOuts) = target.batched(batchBits)
            val newOutsWithGradient =
              newOuts.zipWithIndex.flatMap {
                case (o, j) =>
                  Compiler.withGradient(s"target${i}_bit${j}", o, gradVars)
              }
            (ins ++ newIns, outs ++ newOutsWithGradient)
        }

    val cf = compile(
      targets.variables ++ batchVariables,
      Compiler.withGradient("base", targets.base, gradVars) ++ batchOutputs)
    val numOutputs =
      if (gradient)
        targets.variables.size + 1
      else
        1
    DataFunction(cf, batchBits, targets.variables.size, numOutputs, data)
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
                   variables: List[Variable]): List[(String, Real)] =
    if (variables.isEmpty)
      List((name, real))
    else
      (name, real) :: Gradient
        .derive(variables, real)
        .zipWithIndex
        .map {
          case (g, i) =>
            (s"${name}_grad${i}", g)
        }
}
