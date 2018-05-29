package com.stripe.rainier.sampler

import com.stripe.rainier.compute._
import com.stripe.rainier.ir.CompiledFunction

private[sampler] case class LeapFrog(nVars: Int,
                                     initialHalfThenFullStep: CompiledFunction,
                                     twoFullSteps: CompiledFunction,
                                     finalHalfStep: CompiledFunction) {
  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
  array(n*2+1) == stepSize
   */
  private val potentialIndex = nVars * 2
  private val stepSizeIndex = potentialIndex + 1
  private val inputOutputSize = stepSizeIndex + 1
  private val buf1 = new Array[Double](inputOutputSize)
  private val buf2 = new Array[Double](inputOutputSize)

  private val globalsSize =
    List(initialHalfThenFullStep, twoFullSteps, finalHalfStep)
      .map(_.numGlobals)
      .max
  private val globals = new Array[Double](globalsSize)

  def stepOnce(params: Array[Double], stepSize: Double): Double = {
    System.arraycopy(params, 0, buf1, 0, inputOutputSize)
    buf1(stepSizeIndex) = stepSize
    initialHalfThenFullStep(buf1, globals, buf2)
    finalHalfStep(buf2, globals, params)
    logAcceptanceProb(buf1, params)
  }

  def step(params: Array[Double], n: Int, stepSize: Double)(
      implicit rng: RNG): Double = {
    System.arraycopy(params, 0, buf1, 0, inputOutputSize)
    buf1(stepSizeIndex) = stepSize
    initializePs(buf1)
    initialHalfThenFullStep(buf1, globals, buf2)
    var i = 1
    var in = buf2
    var out = buf1
    while (i < n) {
      twoFullSteps(in, globals, out)
      val tmp = in; in = out; out = tmp
      i += 1
    }
    finalHalfStep(in, globals, out)
    val p = logAcceptanceProb(params, out)
    if (p > Math.log(rng.standardUniform))
      System.arraycopy(out, 0, params, 0, inputOutputSize)
    p
  }

  def variables(array: Array[Double]): Array[Double] = {
    val newArray = new Array[Double](nVars)
    var i = 0
    while (i < nVars) {
      newArray(i) = array(i + nVars)
      i += 1
    }
    newArray
  }

  //we want the invariant that a params array always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential. We can do that (slightly wastefully)
  //by using initialHalfThenFullStep with a stepSize of 0.0
  def initialize(implicit rng: RNG): Array[Double] = {
    initializePs(buf1)
    buf1(stepSizeIndex) = 0.0
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      buf1(i) = rng.standardNormal
      i += 1
    }
    val array = new Array[Double](inputOutputSize)
    initialHalfThenFullStep(buf1, globals, array)
    array
  }

  private def logAcceptanceProb(from: Array[Double],
                                to: Array[Double]): Double = {
    var deltaH = 0.0
    deltaH += to(potentialIndex) - from(potentialIndex)
    var i = 0
    while (i < nVars) {
      val p1 = from(i)
      val p2 = to(i)
      deltaH += ((p2 * p2) - (p1 * p1)) / 2.0
      i += 1
    }
    if (deltaH.isNaN) { Math.log(0.0) } else { (-deltaH).min(0.0) }
  }

  private def initializePs(array: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
  }
}

private[sampler] object LeapFrog {
  def apply(qs: List[Variable], density: Real): LeapFrog = {
    val ps = qs.map { _ =>
      new Variable
    }
    val potential = new Variable
    val stepSize = new Variable
    val inputs: List[Variable] = (ps ++ qs) :+ potential :+ stepSize

    val newPotential = density * -1
    val grad = newPotential.gradient

    val halfPs =
      ps.zip(newPotential.gradient).map {
        case (p, g) => p - (stepSize / 2) * g
      }
    val halfPsNewQs =
      qs.zip(halfPs).map { case (q, p) => q + (stepSize * p) }

    val initialHalfThenFullStep =
      (halfPs ++ halfPsNewQs) :+ newPotential :+ stepSize

    val fullPs =
      ps.zip(grad).map { case (p, g) => p - stepSize * g }

    val fullPsNewQs =
      qs.zip(fullPs).map { case (q, p) => q + (stepSize * p) }

    val twoFullSteps =
      (fullPs ++ fullPsNewQs) :+ newPotential :+ stepSize

    val finalHalfStep =
      (halfPs ++ qs) :+ newPotential :+ stepSize

    new LeapFrog(
      qs.size,
      Compiler.default.compileUnsafe(inputs, initialHalfThenFullStep),
      Compiler.default.compileUnsafe(inputs, twoFullSteps),
      Compiler.default.compileUnsafe(inputs, finalHalfStep)
    )
  }
}
