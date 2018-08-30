package com.stripe.rainier.core

import com.stripe.rainier.sbc._
import org.scalatest.FunSuite

class SBCNormalTest extends FunSuite {
  import SBCNormalModel._
  test("x => Normal(x,1) with Uniform(0,1) prior") {
    val samples = List(2.929954195471396, -0.0367659663995098,
      -0.0367659663995098, -0.0367659663995098, 0.0477064256473958,
      1.020309845907205, 0.9019208495291298, -1.3828653801854391,
      0.9266159608165953, 0.9266159608165953)
    val newSamples = priors.param.flatMap(fn(_).param).sample(10)
    assert(newSamples == samples)
  }
}
