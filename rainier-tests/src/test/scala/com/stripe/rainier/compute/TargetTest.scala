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
      0.to(2).foreach { i =>
        val batchedFn = Compiler.default.compileTargets(batchedGroup, false, i)
        val inlinedFn = Compiler.default.compileTargets(inlinedGroup, false, i)
        val inputs = Array.fill[Double](batchedFn.numInputs)(0.0)
        val globals = Array.fill[Double](batchedFn.numGlobals)(0.0)
        val outputs = Array[Double](0.0)
        batchedFn(inputs, globals, outputs)
        val batchedOutput = outputs(0)
        inlinedFn(inputs, globals, outputs)
        val inlinedOutput = outputs(0)
        assertWithinEpsilon(batchedOutput, inlinedOutput, s"bits: $i")
      }
    }
  }

  def assertWithinEpsilon(x: Double, y: Double, clue: String): Unit = {
    val relativeError = ((x - y) / x).abs
    if (!(x.isNaN && y.isNaN || relativeError < 0.001))
      assert(x == y, clue)
    ()
  }

  run("normal param") {
    Normal.standard.param.targets
  }

  run("normal fit") {
    Normal.standard.param.flatMap { a =>
      Normal(a, 1).fit(List(1d, 2d, 3d))
    }.targets
  }

  run("uniform normal fit") {
    Uniform.standard.param.flatMap { a =>
      Normal(a, 1).fit(List(1d, 2d, 3d))
    }.targets
  }

  run("poisson fit") {
    Uniform.standard.param.flatMap { a =>
      Poisson(a).fit(List(1L, 2L, 3L))
    }.targets
  }

  run("exponential fit") {
    Uniform.standard.param.flatMap { a =>
      Exponential(a).fit(List(1d, 2d, 3d))
    }.targets
  }

  run("gamma fit") {
    Normal(0.5, 1).param.flatMap { a =>
      Gamma.standard(a).fit(List(1d, 2d, 3d))
    }.targets
  }
}
