package com.stripe.rainier.trace

import com.stripe.rainier.ir._
import com.stripe.rainier.compute._

case class Tracer(compiler: Compiler, gradient: Boolean) {
  def apply(real: Real): Unit = {
    val targetGroup = TargetGroup(List(Target(real)))
    val outputs =
      if (gradient)
        Compiler.withGradient("density", real, targetGroup.parameters)
      else
        List(("density", real))
    Tracer.dump(compiler.compile(targetGroup.parameters, outputs))
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
