package com.stripe.rainier.compute

import org.scalatest._
import com.stripe.rainier.ir._
import com.stripe.rainier.core._

class InlineTest extends FunSuite {
  val rng = new scala.util.Random()

  def run(df: DataFunction, params: Array[Double]): Double = {
    val globals = new Array[Double](df.numGlobals)
    val outputs = new Array[Double](df.numOutputs)
    df(params, globals, outputs)
    outputs(0)
  }

  def assertWithinEpsilon(x: Double, y: Double): Unit = {
    val relativeError = ((x - y) / x).abs
    if (!(x.isNaN && y.isNaN || relativeError < 0.001))
      assert(x == y)
    ()
  }

  def check(description: String)(fn: Real => Real): Unit = {
    val ys = 1.to(1000)
    test(description) {
      val ph1 = Real.doubles(ys.map(_.toDouble))
      val ph2 = Real.longs(ys.map(_.toLong))
      val result1 = fn(ph1)
      val result2 = fn(ph2)
      val df1 = Compiler.default
        .compileTargets(TargetGroup(List(Target(result1, true))), false)
      val df2 = Compiler.default
        .compileTargets(TargetGroup(List(Target(result1, false))), false)
      val df3 = Compiler.default
        .compileTargets(TargetGroup(List(Target(result2, true))), false)
      val df4 = Compiler.default
        .compileTargets(TargetGroup(List(Target(result2, false))), false)
      val xs = new Array[Double](df2.numInputs)
      var i = 0
      while (i < df2.numParamInputs) {
        xs(i) = rng.nextDouble()
        i += 1
      }
      val res1 = run(df1, xs)
      val res2 = run(df2, xs)
      val res3 = run(df3, xs)
      val res4 = run(df4, xs)
      assertWithinEpsilon(res1, res2)
      assertWithinEpsilon(res2, res3)
      assertWithinEpsilon(res3, res4)
    }
  }

  check("identity") { x =>
    x
  }

  check("LogNormal") { x =>
    val y = LogNormal(0, 1).param
    LogNormal(y, y).logDensity(x)
  }

  check("factorial") { x =>
    Combinatorics.factorial(x)
  }
}
