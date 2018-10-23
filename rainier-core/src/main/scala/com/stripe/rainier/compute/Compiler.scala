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

  def compileTargets(targets: Iterable[Target],
                     gradient: Boolean,
                     batchBits: Int): (Seq[Variable], DataFunction) = {
    val (base, batched) =
      targets.foldLeft((Real.zero, List.empty[Target])) {
        case ((b, l), t) =>
          t.maybeInlined match {
            case Some(r) => ((b + r), l)
            case None    => (b, t :: l)
          }
      }

    val variables =
      batched
        .foldLeft(RealOps.variables(base)) {
          case (set, target) =>
            set ++ target.variables
        }
        .toList
        .sortBy(_.param.sym.id)

    val df = compileTargets(base, batched, variables, gradient, batchBits)
    (variables, df)
  }

  def compileTargets(base: Real,
                     batched: List[Target],
                     variables: List[Variable],
                     gradient: Boolean,
                     batchBits: Int): DataFunction = {

    def withGradient(name: String, real: Real): List[(String, Real)] =
      if (gradient)
        (name, real) :: Gradient.derive(variables, real).zipWithIndex.map {
          case (g, i) =>
            (name + "_" + "grad" + i, g)
        } else
        List((name, real))

    val (batchVariables, batchOutputs) =
      batched.zipWithIndex.foldLeft(
        (List.empty[Variable], List.empty[(String, Real)])) {
        case ((ins, outs), (target, i)) =>
          val (newIns, newOuts) = target.batched(batchBits)
          val newOutsWithGradient =
            newOuts.zipWithIndex.flatMap {
              case (o, j) =>
                withGradient("target" + i + "_bit" + j, o)
            }
          (ins ++ newIns, outs ++ newOutsWithGradient)
      }

    val data = batched.map { target =>
      target.placeholderVariables.map { v =>
        target.placeholders(v)
      }.toArray
    }.toArray

    val cf = compile(variables ++ batchVariables,
                     withGradient("base", base) ++ batchOutputs)
    val numOutputs =
      if (gradient)
        variables.size + 1
      else
        1
    DataFunction(cf, batchBits, variables.size, numOutputs, data)
  }

  def compile(inputs: Seq[Variable],
              outputs: Seq[(String, Real)]): CompiledFunction = {
    logger
      .atInfo()
      .log("Compiling method with %d inputs and %d outputs",
           inputs.size,
           outputs.size)

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
}
