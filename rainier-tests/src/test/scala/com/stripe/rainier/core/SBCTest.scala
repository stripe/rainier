package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  def check(sbcModel: SBCModel): Unit = {
    test(sbcModel.description) {
      assert(sbcModel.samples == sbcModel.goldset)
    }
  }

  check(SBCUniformNormal)
  check(SBCLogNormal)
}
