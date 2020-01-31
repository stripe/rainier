package com.stripe.rainier.decompile

import com.stripe.rainier.ir._
import com.stripe.rainier.compute._
import com.stripe.rainier.core._

case class Decompiler(compiler: Compiler, filter: String => Boolean) {
  def apply(model: Model): Unit = apply(model.targetGroup)
  def apply(targetGroup: TargetGroup): Unit = {
    val outputs = targetGroup.outputs.filter { case (s, _) => filter(s) }
    Decompiler.dump(compiler.compile(targetGroup.inputs, outputs))
  }
}

object Decompiler {
  def dump(cf: CompiledFunction): Unit =
    cf.getClass.getClassLoader match {
      case cl: GeneratedClassLoader =>
        CFR.decompile(cl.bytecode).foreach(println)
      case _ =>
        sys.error("Cannot find bytecode for class")
    }

  val default: Decompiler = Decompiler(Compiler.default, s => !s.contains("grad"))
  val gradient: Decompiler = Decompiler(Compiler.default, _ => true)
}
