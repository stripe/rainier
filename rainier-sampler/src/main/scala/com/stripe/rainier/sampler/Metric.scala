package com.stripe.rainier.sampler

sealed trait Metric

object Metric {
  def velocity(in: Array[Double], out: Array[Double], m: Metric): Unit =
    m match {
      case StandardMetric =>
        System.arraycopy(in, 0, out, 0, out.size)
      case EuclideanMetric(elements) =>
        ???
    }

  def energy(v: Array[Double], m: Metric): Double = {
    val n = v.size
    var k = 0.0
    m match {
      case StandardMetric =>
        var i = 0
        while (i < n) {
          val x = v(i)
          k += (x * x)
          i += 1
        }
      case EuclideanMetric(elements) => ???
    }
    k
  }
}

object StandardMetric extends Metric
case class EuclideanMetric(elements: Array[Double]) extends Metric
