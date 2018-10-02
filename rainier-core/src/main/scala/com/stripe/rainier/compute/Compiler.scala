package com.stripe.rainier.compute

import com.stripe.rainier.ir.{CompiledFunction, DataFunction}

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
      CompiledFunction.run(cf, in, globals, out)
      out
    }
    fn
  }

  def compileTargets(targets: Iterable[Target],
                     batchBits: Int): DataFunction = {
    val (base, batched) =
      targets.foldLeft((Real.zero, List.empty[Target])) {
        case ((b, l), t) =>
          t.maybeInlined match {
            case Some(r) => ((b + r), l)
            case None    => (b, t :: l)
          }
      }
    compileTargets(base, batched, batchBits)
  }

  def compileTargets(base: Real,
                     batched: List[Target],
                     batchBits: Int): DataFunction = {
    val variables =
      batched
        .foldLeft(RealOps.variables(base)) {
          case (set, target) =>
            set ++ target.variables
        }
        .toList
        .sortBy(_.param.sym.id)

    val (batchVariables, batchOutputs) =
      batched.foldLeft((List.empty[Variable], List.empty[Real])) {
        case ((ins, outs), target) =>
          val (newIns, newOuts) = target.batched(batchBits)
          (ins ++ newIns, outs ++ newOuts)
      }

    val data = batched.map { target =>
      target.placeholderVariables.map { v =>
        target.placeholders(v)
      }.toArray
    }.toArray

    val cf = compileUnsafe(variables ++ batchVariables, base :: batchOutputs)
    DataFunction(cf, batchBits, variables.size, 1, data)
  }

  def compileUnsafe(inputs: Seq[Variable], outputs: Seq[Real]): CompiledFunction
}

final case class IRCompiler(methodSizeLimit: Int, classSizeLimit: Int)
    extends Compiler {
  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): CompiledFunction = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val irs = outputs.map { r =>
      translator.toIR(r)
    }
    CompiledFunction(params, irs, methodSizeLimit, classSizeLimit)
  }
}
