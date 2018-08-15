package com.stripe.rainier.core

// in the tests module, type run and it it will run sbc
// if run test, runs the test.
// So: q can I set up a situation where it does one thing for run
// and another for test
// maybe if trait object main, then run will work>
// and if object implements whatever FunSuite wants

trait SBCModel[L, T] {

  def priors: Continuous
  def fn: Real => L
}
