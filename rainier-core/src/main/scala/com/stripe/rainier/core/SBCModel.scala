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
    List(-0.9591398101973461, -0.39421416837532947, -0.43135330276363437,
      0.8379655169973288, 0.8787234187840076, -0.7783571706288746,
      0.0010460969318301233, -1.0971318369621517, -0.5462826741670654,
      0.9713291073012085)
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
