package com.stripe.rainier.sampler

trait MassMatrixEstimator {
  def update(sample: Array[Double]): Unit
  def reset(): Unit
  def massMatrix: MassMatrix
}

class CovarianceEstimator(size: Int) extends MassMatrixEstimator {
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

  def covariance: Array[Double] = {
    val elements = new Array[Double](cov.size)
    val z = (variance.samples - 1).toDouble
    var i = 0
    while (i < cov.size) {
      elements(i) = cov(i) / z
      i += 1
    }
    elements
  }

  def massMatrix: MassMatrix = DenseMassMatrix(covariance)
}

class VarianceEstimator(size: Int) extends MassMatrixEstimator {
  var samples = 0
  val mean = new Array[Double](size)
  val raw = new Array[Double](size)

  val oldDiff = new Array[Double](size)
  val newDiff = new Array[Double](size)

  def reset() = {
    var i = 0
    while (i < size) {
      mean(i) = 0.0
      raw(i) = 0.0
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
      raw(j) += oldDiff(j) * newDiff(j)
      j += 1
    }
  }

  //special case used for size=1
  val buf1D = Array(0.0)
  def update(sample: Double): Unit = {
    buf1D(0) = sample
    update(buf1D)
  }

  def variance(): Array[Double] = {
    val elements = new Array[Double](size)
    var i = 0
    while (i < size) {
      elements(i) = raw(i) / samples.toDouble
      i += 1
    }
    elements
  }

  def massMatrix: MassMatrix =
    DiagonalMassMatrix(variance)

  private def diff(sample: Array[Double], buf: Array[Double]): Unit = {
    var i = 0
    while (i < size) {
      buf(i) = sample(i) - mean(i)
      i += 1
    }
  }
}
