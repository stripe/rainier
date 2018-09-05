package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

trait SBCModel[L, T] {
  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def sbc: SBC[L, T]
  def main(args: Array[String]): Unit =
    sbc.animate(HMC(1), 10000, 1000)
//  def samples(nSamples: Int)(
//      implicit sampleable: Sampleable[(L, Real), Double]): List[Double] =
//    sbc.posterior.sample(nSamples)
  def samples(nSamples: Int): List[Double]
  def goldsetSamples: List[Double]
  def agreesWithGoldset: Boolean =
    samples(goldsetSamples.size) == goldsetSamples
  def description: String
}

object SBCUniformNormal extends SBCModel[Continuous, Double] {
  def sbc = SBC(Uniform(0, 1))((x: Real) => Normal(x, 1))
  def samples(nSamples: Int) = List(0.13, 0.11, 2.223)
  def goldsetSamples: List[Double] = List(0.1, 0.33, 1.12)
  def description = "x => Normal(x,1) with Uniform(0,1) prior"
}

//object LogNormal extends SBCModel[Continuous, Double] {
//  def sbc = SBC(LogNormal(0,1))((x: Real) => LogNormal(x,x))
//  def description = "x => LogNormal(x,x) with LogNormal(0,1) prior"
//}
