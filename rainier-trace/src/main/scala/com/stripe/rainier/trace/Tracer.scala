package com.stripe.rainier.trace

import com.stripe.rainier.ir._
import com.stripe.rainier.compute._
import com.stripe.rainier.core._

case class Tracer(compiler: Compiler, gradient: Boolean) {
  def apply(real: Real): Unit = {
    val outputs =
      if (gradient)
        ("density", real) :: real.gradient.zipWithIndex.map {
          case (r, i) =>
            ("grad" + i, r)
        } else
        List(("density", real))
    Tracer.dump(compiler.compile(real.variables, outputs))
  }

  def apply(rv: RandomVariable[_], batchBits: Int = 1): Unit = {
    val (_, df) =
      compiler.compileTargets(rv.targets, gradient, batchBits)
    Tracer.dump(df.cf)
  }

  def output(rv: RandomVariable[_], batchBits: Int = 1): String = {
    val (_, df) =
      compiler.compileTargets(rv.targets, gradient, batchBits)
    Tracer.dumps(df.cf)
  }

}

object Tracer {

  def decompile(cf: CompiledFunction): Seq[String] =
    cf.getClass.getClassLoader match {
      case cl: GeneratedClassLoader =>
        CFR.decompile(cl.bytecode)
      case _ =>
        sys.error("Cannot find bytecode for class")
    }

  def dump(cf: CompiledFunction): Unit =
    decompile(cf).foreach { println }

  def dumps(cf: CompiledFunction): String =
    decompile(cf).mkString("\n")

  val default = Tracer(Compiler.default, false)
  val gradient = Tracer(Compiler.default, true)
}
