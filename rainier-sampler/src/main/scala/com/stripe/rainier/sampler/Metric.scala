package com.stripe.rainier.sampler

sealed trait Metric

object Metric {
  //evaluate x*Mx for input vector x of length n, and metric M
  def xMx(x: Array[Double], m: Metric, n: Int): Double = {
    var k = 0.0
    m match {
      case IdentityMetric =>
        var i = 0
        while (i < n) {
          val p = x(i)
          k += (p * p)
          i += 1
        }
      case _ => ()
    }
    k
  }
}

object IdentityMetric extends Metric
case class DiagonalMetric(elements: Array[Double]) extends Metric
case class DenseMetric(elements: Array[Double]) extends Metric
