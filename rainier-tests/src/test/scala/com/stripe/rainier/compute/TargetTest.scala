package com.stripe.rainier.compute

import org.scalatest._

import com.stripe.rainier.core._

class TargetTest extends FunSuite {
  def run(description: String)(targets: Iterable[Target]): Unit = {
    test(description) {
      val batchedGroup = TargetGroup(targets, 0)
      val inlinedGroup = TargetGroup(Real.sum(targets.map(_.inlined)),
                                     Nil,
                                     batchedGroup.variables)
      val batchedFn = Compiler.default.compileTargets(batchedGroup, false, 1)
      val inlinedFn = Compiler.default.compileTargets(inlinedGroup, false, 1)
      val inputs = Array.fill[Double](batchedFn.numInputs)(0.0)
      val globals = Array.fill[Double](batchedFn.numGlobals)(0.0)
      val outputs = Array[Double](0.0)
      batchedFn(inputs, globals, outputs)
      val batchedOutput = outputs(0)
      inlinedFn(inputs, globals, outputs)
      val inlinedOutput = outputs(0)
      assert(batchedOutput == inlinedOutput)
    }
  }

  run("normal param") {
    Normal.standard.param.targets
  }

  run("fit gamma") {
    Normal(0.55, 0.1).param.flatMap { a =>
      Gamma(a, 3).fit(List(3d, 4d, 6d))
    }.targets
  }
}
