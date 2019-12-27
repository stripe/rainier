package com.stripe.rainier.trace

import com.stripe.rainier.ir._
import com.stripe.rainier.compute._

case class Tracer(compiler: Compiler, gradient: Boolean) {
  def apply(real: Real): Unit = {
    val target = Target(real)
    val params = target.parameters.toList
    val variables = params ++ target.placeholders
    val outputs =
      if (gradient)
        Compiler.withGradient("density", real, params)
      else
        List(("density", real))
    Tracer.dump(compiler.compile(variables, outputs))
  }
}

object Tracer {
  def dump(cf: CompiledFunction): Unit =
    cf.getClass.getClassLoader match {
      case cl: GeneratedClassLoader =>
        CFR.decompile(cl.bytecode).foreach(println)
      case _ =>
        sys.error("Cannot find bytecode for class")
    }

  val default: Tracer = Tracer(Compiler.default, false)
  val gradient: Tracer = Tracer(Compiler.default, true)
}
