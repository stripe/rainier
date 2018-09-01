//package com.stripe.rainier.sbc
//
//import com.stripe.rainier.core._
//import com.stripe.rainier.sampler._
//
//object SBCLogNormalModel extends SBCModel[Continuous, Double] {
//
//  implicit val rng: RNG = ScalaRNG(1528673302081L)
//  def priors = LogNormal(0, 1)
//  def fn = x => LogNormal(x, x)
//
//  def main(args: Array[String]): Unit = {
//    SBC[Continuous, Double](priors)(fn).animate(HMC(1), 10000, 1000)
//  }
//}
