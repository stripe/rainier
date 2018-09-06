package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

trait SBCModel[L, T] {
  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def sbc: SBC[L, T]
  def main(args: Array[String]): Unit = sbc.animate(HMC(1), 10000, 1000)
  def samples: List[T] = sbc.posteriorSamples(goldset.size)
  def goldset: List[T]
  def description: String
}

object SBCUniformNormal extends SBCModel[Continuous, Double] {
  def sbc = SBC(Uniform(0, 1))((x: Real) => Normal(x, 1))
  def goldset =
    List(0.017065046149648155, -0.29273619828754743, -1.110853401728609,
      -0.3159474105127094, -0.11552941551367488, 1.0169004294139325,
      0.1580296891718888, -0.15199080795520775, -0.4842696291336046,
      1.1769107111393704)
  def description = "Normal(x,1) with Uniform(0,1) prior"
}

object SBCLogNormal extends SBCModel[Continuous, Double] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset: List[Double] = List(0.1, 0.33, 1.12)
  def description = "LogNormal(x,x) with LogNormal(0,1) prior"
}

object SBCExponential extends SBCModel[Continuous, Double] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => Exponential(x))
  def goldset: List[Double] = List(0.1, 0.33, 1.12)
  def description = "Exponential(x) with LogNormal(0,1) prior"
}

object SBCLaplace extends SBCModel[Continuous, Double] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => Laplace(x, x))
  def goldset: List[Double] = List(0.1, 0.33, 1.12)
  def description = "Laplace(x,x) with LogNormal(0,1) prior"
}
