package com.stripe.rainier.compute

import com.stripe.rainier.ir

trait Compiler {
  def compile(inputs: Seq[Variable], output: Real): Array[Double] => Double =
    compile(inputs, List(output)).andThen { array =>
      array(0)
    }

  def compileGradient(inputs: Seq[Variable],
                      output: Real): Array[Double] => (Double, Array[Double]) =
    compile(inputs, output :: Gradient.derive(inputs, output).toList).andThen {
      array =>
        (array.head, array.tail)
    }

  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double]

}

object Compiler {
  var default: Compiler = IRCompiler(200, false)
}

case class InstrumentingCompiler(orig: Compiler, printEvery: Int)
    extends Compiler {
  var count = 0L
  var nanos = 0L
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val cf = orig.compile(inputs, outputs)
    val fn = { array: Array[Double] =>
      count += 1
      val t1 = System.nanoTime
      val result = cf(array)
      val t2 = System.nanoTime
      nanos += (t2 - t1)
      if (count % printEvery == 0) {
        println(s"[InstrumentingCompiler] $count runs, ${nanos / count} ns/run")
      }
      result
    }
    fn
  }
}

case class IRCompiler(methodSizeLimit: Int, writeToTmpFile: Boolean)
    extends Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val irs = outputs.map { r =>
      translator.toIR(r)
    }
    ir.ClassGenerator.generate(params, irs, methodSizeLimit, writeToTmpFile)
  }
}
