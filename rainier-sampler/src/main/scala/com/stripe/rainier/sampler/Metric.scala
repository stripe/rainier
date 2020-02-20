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

  def halfEnergy(v: Array[Double], m: Metric): Double = {
    val n = v.size
    m match {
      case StandardMetric =>
        var k = 0.0
        var i = 0
        while (i < n) {
          val x = v(i)
          k += (x * x)
          i += 1
        }
        k / 2.0
      case EuclideanMetric(elements) => ???
    }
  }
}

object StandardMetric extends Metric
case class EuclideanMetric(elements: Array[Double]) extends Metric
