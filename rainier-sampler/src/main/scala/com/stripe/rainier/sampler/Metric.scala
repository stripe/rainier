package com.stripe.rainier.sampler

sealed trait Metric

object StandardMetric extends Metric

case class EuclideanMetric(elements: Array[Double]) extends Metric {
  require(!elements.contains(0.0))
}

class StandardMetricTuner extends MetricTuner {
  def initialize(lf: LeapFrog): Metric = StandardMetric
  def update(sample: Array[Double]): Option[Metric] = None
  def metric: Metric = StandardMetric
}
