package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class SBCTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(123L)

  def check(description: String)(run: SBCRun) = {
    run.animate(3)
    //when this test fails, if the calibration looked good,
    //plug the rHat value listed in the output into the call
    assert(false)
  }

  def check(description: String, rHat: Double)(run: SBCRun) = {
    //when this fails, delete the rHat parameter to see a new run
    assert(rHat == run.rHat)
  }

  val normal = SBC(LogNormal(0, 1)) { sd =>
    Normal(0, sd)
  }

  check("Normal with HMC") {
    normal.prepare(HMC(5), 1000, 1000)
  }
}
