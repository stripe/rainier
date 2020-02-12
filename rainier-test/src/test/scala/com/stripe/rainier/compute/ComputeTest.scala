package com.stripe.rainier.compute

import org.scalatest._

trait ComputeTest extends FunSuite {
  def assertWithinEpsilon(x: Double, y: Double, clue: => String): Unit = {
    if (x.abs > 10E-8 || y.abs > 10E-8) {
      val relativeError = ((x - y) / x).abs
      if (!(x.isNaN && y.isNaN || relativeError < 0.001))
        assert(x == y, clue)
      ()
    } else {
      // both values really close to 0, consider equal to
      // account for inaccuracies in numeric differentiation
      ()
    }
  }
}
