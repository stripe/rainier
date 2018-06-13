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

final case class InstrumentingCompiler(orig: Compiler, printEvery: Int)
    extends Compiler {
  var count: Long = 0L
  var nanos: Long = 0L
  def compileUnsafe(inputs: Seq[Variable],
                    outputs: Seq[Real]): ir.CompiledFunction = {
    val cf = orig.compileUnsafe(inputs, outputs)
    new ir.CompiledFunction {
      val numInputs = cf.numInputs
      val numGlobals = cf.numGlobals
      val numOutputs = cf.numOutputs
      def apply(inputs: Array[Double],
                globals: Array[Double],
                outputs: Array[Double]): Unit = {
        count += 1
        val t1 = System.nanoTime
        cf(inputs, globals, outputs)
        val t2 = System.nanoTime
        nanos += (t2 - t1)
        if (count % printEvery == 0) {
          println(
            s"[InstrumentingCompiler] $count runs, ${nanos / count} ns/run")
        }
      }
    }
  }
}

final case class IRCompiler(methodSizeLimit: Int,
                            classSizeLimit: Int,
                            writeToTmpFiles: Boolean)
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
    ir.CompiledFunction(params,
                        irs,
                        methodSizeLimit,
                        classSizeLimit,
                        writeToTmpFiles)
  }
}
