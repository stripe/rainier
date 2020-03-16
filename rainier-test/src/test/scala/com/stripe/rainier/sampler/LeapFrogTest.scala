package com.stripe.rainier.sampler

import org.scalatest.FunSuite

class NormalDensityFunction extends DensityFunction {
  val nVars = 1
  var x = 0.0
  def update(vars: Array[Double]): Unit = {
    x = vars(0)
  }
  def density = (x * x) / -2.0
  def gradient(index: Int) = -x
}

class LeapFrogTest extends FunSuite {
  implicit val rng = new ScalaRNG(123L)

  def run(df: DensityFunction,
          stepSize: Double,
          mass: MassMatrix): List[Double] = {
    val lf = new LeapFrog(df, 100)
    var i = 0
    val params = lf.initialize(mass)
    var output = List.empty[Double]
    while (i < 1000) {
      lf.startIteration(params, mass)
      lf.takeSteps(1, stepSize, mass)
      lf.finishIteration(params, mass)
      val out = new Array[Double](df.nVars)
      lf.variables(params, out)
      output = out(0) :: output
      i += 1
    }
    output
  }

  def variance(mean: Double, seq: Seq[Double]): Double = {
    seq.map { x =>
      math.pow(x - mean, 2)
    }.sum / (seq.size - 1)
  }

  def check(df: DensityFunction,
            stepSize: Double,
            mass: MassMatrix,
            mean: Double,
            epsMean: Double,
            vari: Double,
            epsVari: Double): Unit = {
    val list = run(df, stepSize, mass)
    val m = list.sum / list.size
    val v = variance(mean, list)
    val meanErr = Math.abs(mean - m)
    val varErr = Math.abs(vari - v)
    assert(meanErr < epsMean)
    assert(varErr < epsVari)
    ()
  }

  test("standard normal, identity matrix") {
    check(new NormalDensityFunction,
          1.0,
          IdentityMassMatrix,
          0.0,
          0.2,
          1.0,
          0.2)
  }

  test("standard normal, diagonal matrix") {
    check(new NormalDensityFunction,
          1.0,
          DiagonalMassMatrix(Array(0.1)),
          0.0,
          0.2,
          1.0,
          0.3)
  }
}
