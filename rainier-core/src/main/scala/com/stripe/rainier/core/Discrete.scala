package com.stripe.rainier.core

import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.RNG

case class Poisson(lambda: Real) extends Distribution[Int] {
  def logDensity(t: Int): Real = {
    lambda.log * t - lambda - Combinatrics.factorial(t)
  }

  val generator = Generator.require(Set(lambda)) { (r, n) =>
    val l = math.exp(-n.toDouble(lambda))
    var k = 0
    var p = 1.0
    while (p > l) {
      k += 1
      p *= r.standardUniform
    }
    k - 1
  }
}
