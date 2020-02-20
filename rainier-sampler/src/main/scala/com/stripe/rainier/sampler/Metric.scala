package com.stripe.rainier.sampler

sealed trait Metric
object StandardMetric extends Metric
case class EuclideanMetric(elements: Array[Double]) extends Metric

object EuclideanMetric {
  def standard(n: Int): Metric =
    EuclideanMetric(0.until(n).toArray.flatMap { i =>
      val row = Array.fill(n)(0.0)
      row(i) = 1.0
      row
    })
}
