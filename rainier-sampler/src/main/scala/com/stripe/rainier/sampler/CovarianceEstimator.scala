package com.stripe.rainier.sampler

class CovarianceEstimator(size: Int) {
  val mean = new Array[Double](size)
  val oldDiff = new Array[Double](size)
  val newDiff = new Array[Double](size)
  val cov = Array.fill(size * size)(0.0)
  var samples = 0

  private def diff(sample: Array[Double], buf: Array[Double]): Unit = {
    var i = 0
    while (i < size) {
      buf(i) = sample(i) - mean(i)
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
    var l = 0
    while (j < size) {
      var k = 0
      while (k < size) {
        cov(j * size + k) += newDiff(j) * oldDiff(k)
        k += 1
        l += 1
      }
      j += 1
    }
  }

  def close(): Array[Double] = {
    val nn = cov.size
    val z = (samples - 1).toDouble
    var i = 0
    while (i < nn) {
      cov(i) /= z
      i += 1
    }
    cov
  }
}
