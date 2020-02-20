package com.stripe.rainier.sampler

sealed trait Metric
object StandardMetric extends Metric
case class EuclideanMetric(elements: Array[Double]) extends Metric

object EuclideanMetric {
  def standard(n: Int): Metric =
    EuclideanMetric(0.until(n).toArray.flatMap { i =>
      Array.fill(i)(0.0) :+ 1.0
    })
}
