package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  val Epsilon = 0.005
  def check[T](sbcModel: SBCModel[T]): Unit = {
      sbcModel.samples.zip(sbcModel.goldset).foreach {
        case (a, b) =>
          val err = Math.abs((a - b) / b)
          assert(err < Epsilon)
      }
    }
  }

  /** Continuous **/
  check(SBCUniformNormal)
  //check(SBCLogNormal)
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
  check(SBCLargePoisson)
}
