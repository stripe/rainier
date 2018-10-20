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

    def withGradient(real: Real): List[Real] =
      if (gradient)
        real :: Gradient.derive(variables, real)
      else
        List(real)

    val (batchVariables, batchOutputs) =
      batched.foldLeft((List.empty[Variable], List.empty[Real])) {
        case ((ins, outs), target) =>
          val (newIns, newOuts) = target.batched(batchBits)
          (ins ++ newIns, outs ++ newOuts.flatMap(withGradient))
      }

    val data = batched.map { target =>
      target.placeholderVariables.map { v =>
        target.placeholders(v)
      }.toArray
    }.toArray

    val cf = compileUnsafe(variables ++ batchVariables,
                           withGradient(base) ++ batchOutputs)
    val numOutputs =
      if (gradient)
        variables.size + 1
      else
        1
    DataFunction(cf, batchBits, variables.size, numOutputs, data)
  }

  def compileUnsafe(inputs: Seq[Variable], outputs: Seq[Real]): CompiledFunction
}

object Compiler {
  def default: Compiler = IRCompiler(200, 100)
}

final case class IRCompiler(methodSizeLimit: Int, classSizeLimit: Int)
    extends Compiler {
  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): CompiledFunction = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val exprs = outputs.map { r =>
      translator.toExpr(r)
    }
    CompiledFunction(params, exprs, methodSizeLimit, classSizeLimit)
  }
}

final case class InstrumentingCompiler(orig: Compiler, printEvery: Int)
    extends Compiler {
  var count: Long = 0L
  var nanos: Long = 0L
  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): CompiledFunction = {
    val cf = orig.compileUnsafe(inputs, outputs)
    new CompiledFunction {
      val numInputs = cf.numInputs
      val numGlobals = cf.numGlobals
      val numOutputs = cf.numOutputs
      def output(inputs: Array[Double],
                 globals: Array[Double],
                 output: Int): Double = {
        count += 1
        val t1 = System.nanoTime
        val result = cf.output(inputs, globals, output)
        val t2 = System.nanoTime
        nanos += (t2 - t1)
        if (count % printEvery == 0) {
          println(
            s"[InstrumentingCompiler] $count runs, ${nanos / count} ns/run")
        }
        result
      }
    }
  }
}
