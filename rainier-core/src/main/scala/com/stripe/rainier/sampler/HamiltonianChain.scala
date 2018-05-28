package com.stripe.rainier.sampler

import com.stripe.rainier.compute._
import com.stripe.rainier.ir.CompiledFunction

final private class HamiltonianChain(lf: LeapFrog)(implicit rng: RNG) {
  private var params = lf.initialize
  private var newParams = params.clone

  private def accept(): Unit = {
    val tmp = params
    params = newParams
    newParams = tmp
  }

  // Take a single leapfrog step without re-initializing momenta
  // for use in tuning the step size
  def stepOnce(stepSize: Double): Double = {
    lf.steps(1, params, newParams, stepSize, false)
    val logAcceptanceProb = lf.logAcceptanceProb(params, newParams)
    accept()
    logAcceptanceProb
  }

  def step(stepSize: Double, nSteps: Int): Double = {
    lf.steps(nSteps, params, newParams, stepSize, true)
    val logAcceptanceProb = lf.logAcceptanceProb(params, newParams)
    if (Math.log(rng.standardUniform) < logAcceptanceProb)
      accept()
    logAcceptanceProb
  }

  def variables: Array[Double] = lf.variables(params)

  override def clone: HamiltonianChain = new HamiltonianChain(lf)
}

private[sampler] object HamiltonianChain {
  def apply(variables: Seq[Variable], density: Real)(
      implicit rng: RNG): HamiltonianChain = {
    val lf = LeapFrog(variables.toList, density)
    new HamiltonianChain(lf)
  }
}

/*
Params layout:
array(0..(n-1)) == ps
array(n..(n*2-1)) == qs
array(n*2) == potential
array(n*2+1) == stepSize
 */
private class LeapFrog(
    nVars: Int,
    initialHalfThenFullStep: CompiledFunction,
    twoFullSteps: CompiledFunction,
    finalHalfStep: CompiledFunction
) {
  private val potentialIndex = nVars * 2
  private val stepSizeIndex = potentialIndex + 1
  private val inputOutputSize = stepSizeIndex + 1
  private val ioBuf = new Array[Double](inputOutputSize)

  private val globalsSize =
    List(initialHalfThenFullStep, twoFullSteps, finalHalfStep)
      .map(_.numGlobals)
      .max
  private val globals = new Array[Double](globalsSize)

  //we're allowed to clobber to but need to preserve from
  def steps(n: Int,
            from: Array[Double],
            to: Array[Double],
            stepSize: Double,
            initPs: Boolean)(implicit rng: RNG): Unit = {
    System.arraycopy(from, 0, to, 0, inputOutputSize)
    if (initPs)
      initializePs(to, stepSize)
    else
      to(stepSizeIndex) = stepSize

    initialHalfThenFullStep(to, globals, ioBuf)
    var i = 1
    while (i < n) {
      System.arraycopy(ioBuf, 0, to, 0, inputOutputSize)
      twoFullSteps(to, globals, ioBuf)
      i += 1
    }
    finalHalfStep(ioBuf, globals, to)
  }

  //we want the invariant that a params array always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential. We can do that (slightly wastefully)
  //by using initialHalfThenFullStep with a stepSize of 0.0
  def initialize(implicit rng: RNG): Array[Double] = {
    initializePs(ioBuf, 0.0)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      ioBuf(i) = rng.standardNormal
      i += 1
    }
    val array = new Array[Double](inputOutputSize)
    initialHalfThenFullStep(ioBuf, globals, array)
    array
  }

  def initializePs(array: Array[Double], stepSize: Double)(
      implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
    array(stepSizeIndex) = stepSize
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

  def logAcceptanceProb(from: Array[Double], to: Array[Double]): Double = {
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
}

private object LeapFrog {
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
