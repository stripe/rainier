package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

//object SBCNormalTest extends SBCModel[Continuous, Double] with FunSuite {
//  def priors = Uniform(0,1)
//  def fn = x => Normal(x,1)
//  def main(args: Array[String]): Unit = {
//    SBC[Continuous, Double](Uniform(0, 1)) { x =>
//      Normal(x, 1)
//    }.animate(HMC(1), 10000, 1000)
//  }
//  test("Normal with Uniform prior"){
//    val samples = List(2.929954195471396, -0.0367659663995098, -0.0367659663995098, -0.0367659663995098, 0.0477064256473958, 1.020309845907205, 0.9019208495291298, -1.3828653801854391, 0.9266159608165953, 0.9266159608165953)
//    val newSamples = priors.params.flatMap{ x => fn(x).params }.sample(10)
//    assert(newSamples == samples)
//  }
//}
