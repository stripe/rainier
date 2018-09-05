package com.stripe.rainier.core

import org.scalatest.FunSuite

class SBCTest extends FunSuite {

  def check[L, T](sbcModel: SBCModel[L, T]): Unit = {
    println(sbcModel.description)
    test(sbcModel.description) {
      assert(sbcModel.agreesWithGoldset)
    }
  }

  check(SBCUniformNormal)

}

