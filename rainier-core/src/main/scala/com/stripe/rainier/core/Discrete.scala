package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

/**
  * Poisson distribution with expectation `lambda`
  *
  * @param lambda The mean of the Poisson distribution
  */
final case class Poisson(lambda: Real) extends Distribution[Int, Real] {
  def logDensity(v: Real): Real = {
    lambda.log * v - lambda - Combinatorics.factorial(v)
  }

  val generator: Generator[Int] =
    Generator.require(Set(lambda)) { (r, n) =>
      val l = math.exp(-n.toDouble(lambda))
      if (l >= 1.0) { 0 } else {
        var k = 0
        var p = 1.0
        while (p > l) {
          k += 1
          p *= r.standardUniform
        }
        k - 1
      }
    }
}
