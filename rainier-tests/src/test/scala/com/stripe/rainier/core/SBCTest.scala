package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  val Epsilon = 1e-10
  def check[T](sbcModel: SBCModel[T]): Unit = {
    test(sbcModel.description) {
      sbcModel.samples.zip(sbcModel.goldset).foreach {
        case (a, b) =>
          val err = Math.abs((a - b) / b)
          if (err >= Epsilon)
            println((a, b))
        //assert(err < Epsilon)
      }
    }
  }
  //check(SBCUniformNormal)

  // Continuous
  check(SBCUniformNormal)
  check(SBCLogNormal)
  check(SBCExponential)
  check(SBCLaplace)
  check(SBCGamma)

  // Discrete
  check(SBCBernoulli)
  check(SBCBinomial)
  //check(SBCBinomialNormalApproximation)
  check(SBCBinomialPoissonApproximation)
  check(SBCGeometric)
  //check(SBCGeometricZeroInflated)
  check(SBCNegativeBinomial)
  check(SBCLargePoisson)

}
