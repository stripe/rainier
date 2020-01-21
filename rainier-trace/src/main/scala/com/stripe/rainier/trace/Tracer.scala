package com.stripe.rainier.trace

import com.stripe.rainier.ir._
import com.stripe.rainier.compute._
import com.stripe.rainier.core._

case class Tracer(compiler: Compiler, filter: String => Boolean) {
  def apply(model: Model): Unit = apply(model.targetGroup)
  def apply(targetGroup: TargetGroup): Unit = {
    val outputs = targetGroup.outputs.filter { case (s, _) => filter(s) }
    Tracer.dump(compiler.compile(targetGroup.inputs, outputs))
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

  val default: Tracer = Tracer(Compiler.default, s => !s.contains("grad"))
  val gradient: Tracer = Tracer(Compiler.default, _ => true)
}
