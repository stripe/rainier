package com.stripe.rainier.sampler

sealed trait Metric
object StandardMetric extends Metric
case class DiagonalMetric(elements: Array[Double]) extends Metric {
  require(!elements.contains(0.0))
}
case class EuclideanMetric(elements: Array[Double]) extends Metric {
  require(!elements.contains(0.0))
}

trait MetricEstimator {
  def update(sample: Array[Double]): Unit
  def reset(): Unit
  def metric: Metric
}

class VarianceEstimator(size: Int) extends MetricEstimator {
  var samples = 0
  val mean = new Array[Double](size)
  val variance = new Array[Double](size)

  val oldDiff = new Array[Double](size)
  val newDiff = new Array[Double](size)

  def reset() = {
    var i = 0
    while (i < size) {
      mean(i) = 0.0
      variance(i) = 0.0
      i += 1
    }
  }

  def update(sample: Array[Double]): Unit = {
    samples += 1
    diff(sample, oldDiff)
    var i = 0
    while (i < size) {
      mean(i) += (oldDiff(i) / samples.toDouble)
      i += 1
    }
    diff(sample, newDiff)

    var j = 0
    while (j < size) {
      variance(j) += oldDiff(j) * newDiff(j)
      j += 1
    }
  }

  def metric: Metric = {
    val elements = new Array[Double](size)
    var i = 0
    while (i < size) {
      elements(i) = variance(i) / samples.toDouble
      i += 1
    }
    DiagonalMetric(elements)
  }

  private def diff(sample: Array[Double], buf: Array[Double]): Unit = {
    var i = 0
    while (i < size) {
      buf(i) = sample(i) - mean(i)
      i += 1
    }
  }
}

class CovarianceEstimator(size: Int) extends MetricEstimator {
  val variance = new VarianceEstimator(size)
  val cov = Array.fill(size * size)(0.0)

  def reset(): Unit = {
    variance.reset()
    var i = 0
    while (i < cov.size) {
      cov(i) = 0.0
      i += 1
    }
  }

  def update(sample: Array[Double]): Unit = {
    variance.update(sample)

    var j = 0
    var l = 0
    while (j < size) {
      var k = 0
      while (k < size) {
        cov(j * size + k) += variance.newDiff(j) * variance.oldDiff(k)
        k += 1
        l += 1
      }
      j += 1
    }
  }

  def metric: Metric = {
    val elements = new Array[Double](cov.size)
    val z = (variance.samples - 1).toDouble
    var i = 0
    while (i < cov.size) {
      elements(i) = cov(i) / z
      i += 1
    }
    EuclideanMetric(cov)
  }
}
