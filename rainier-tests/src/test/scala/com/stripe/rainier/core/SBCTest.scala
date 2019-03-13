package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  def check(sbcModel: SBCModel): Unit = {
    test(sbcModel.description) {
      assert(sbcModel.samples == sbcModel.goldset)
    }
  }

  /** Continuous **/
  check(SBCUniformNormal)
  check(SBCLogNormal)
  check(SBCExponential)
  check(SBCLaplace)
  check(SBCGamma)

  /** Discrete **/
  check(SBCBernoulli)
  check(SBCBinomial)
  //check(SBCBinomialNormalApproximation)
  check(SBCBinomialPoissonApproximation)
  check(SBCGeometric)
  //check(SBCGeometricZeroInflated)
  check(SBCNegativeBinomial)
}
