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
            (s"grad$i", r)
        } else
        List(("density", real))
    Tracer.dump(compiler.compile(real.variables, outputs))
  }

  def apply(rv: RandomVariable[_], batchBits: Int = 1): Unit = {
    val df =
      compiler.compileTargets(rv.targetGroup, gradient, batchBits)
    Tracer.dump(df.cf)
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
