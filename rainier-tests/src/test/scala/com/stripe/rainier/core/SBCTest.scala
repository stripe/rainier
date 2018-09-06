package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  def check[L, T](sbcModel: SBCModel[L, T]): Unit = {
    test(sbcModel.description) {
      assert(sbcModel.samples == sbcModel.goldset)
    }
  }

  check(SBCUniformNormal)
  check(SBCLogNormal)
  check(SBCExponential)
  check(SBCLaplace)
}
