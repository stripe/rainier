package com.stripe.rainier.sampler

sealed trait MassMatrix

object IdentityMassMatrix extends MassMatrix

case class DiagonalMassMatrix(elements: Array[Double]) extends MassMatrix {
  require(!elements.contains(0.0))

  val stdDevs = elements.map { x =>
    Math.sqrt(x)
  }
}

case class DenseMassMatrix(elements: Array[Double]) extends MassMatrix {
  require(!elements.contains(0.0))

  val choleskyUpperTriangular =
    DenseMassMatrix.choleskyUpperTriangular(elements)

  override def toString = {
    val n = DenseMassMatrix.matrixSize(elements)
    val rows = elements.grouped(n).map { row =>
      row
        .map { v =>
          f"${v}% 10.5f"
        }
        .mkString(" ")
    }
    "[" + rows.mkString("\n ") + "]"
  }
}

object DenseMassMatrix {
  def squareMultiply(matrix: Array[Double],
                     vector: Array[Double],
                     out: Array[Double]): Unit = {
    val n = out.size
    var i = 0
    while (i < n) {
      var y = 0.0
      var j = 0
      while (j < n) {
        y += vector(j) * matrix((i * n) + j)
        j += 1
      }
      out(i) = y
      i += 1
    }
  }

  private def triangleNumber(k: Int): Int = (k * (k + 1)) / 2

  def upperTriangularSolve(packed: Array[Double],
                           vector: Array[Double],
                           out: Array[Double]): Unit = {
    var i = vector.size - 1
    var m = triangleNumber(i + 1) - 1
    while (i >= 0) {
      var j = vector.size - 1
      var dot = 0.0
      while (j > i) {
        dot += out(j) * packed(m)
        j -= 1
        m -= 1
      }
      out(i) = (vector(i) - dot) / packed(m)
      i -= 1
      m -= 1
    }
  }

  def matrixSize(elements: Array[Double]): Int =
    math.floor(math.sqrt(elements.size.toDouble)).toInt

  def choleskyUpperTriangular(matrix: Array[Double]): Array[Double] = {
    val n = matrixSize(matrix)
    val lower = new Array[Double](triangleNumber(n))
    var i = 0
    var l = 0
    while (i < n) {
      var k = 0
      while (k <= i) {
        var sum = 0.0
        var j = 0
        while (j < k) {
          sum += lower(triangleNumber(i) + j) * lower(triangleNumber(k) + j)
          j += 1
        }
        val x = matrix((i * n) + k) - sum
        lower(l) =
          if (i == k)
            math.sqrt(x)
          else {
            val diag = lower(triangleNumber(k + 1) - 1)
            (1.0 / diag * x)
          }
        k += 1
        l += 1
      }
      i += 1
    }

    val upper = new Array[Double](lower.size)
    i = 0
    l = 0
    while (i < n) {
      var k = 0
      while (k < (n - i)) {
        upper(l) = lower(triangleNumber(k + i) + i)
        k += 1
        l += 1
      }
      i += 1
    }
    upper
  }
}

class IdentityMassMatrixTuner extends MassMatrixTuner {
  def initialize(lf: LeapFrog, iterations: Int): MassMatrix = IdentityMassMatrix
  def update(sample: Array[Double]): Option[MassMatrix] = None
  def massMatrix: MassMatrix = IdentityMassMatrix
}

trait WindowedMassMatrixTuner extends MassMatrixTuner {
  def initialWindowSize: Int
  def windowExpansion: Double
  def skipFirst: Int
  def skipLast: Int

  var prevMassMatrix: MassMatrix = IdentityMassMatrix
  var estimator: MassMatrixEstimator = _
  var windowSize = initialWindowSize
  var i = 0
  var j = 0
  var totalIterations = 0

  def initialize(lf: LeapFrog, iterations: Int): MassMatrix = {
    estimator = initializeEstimator(lf.nVars)
    totalIterations = iterations
    IdentityMassMatrix
  }

  def initializeEstimator(size: Int): MassMatrixEstimator

  def update(sample: Array[Double]): Option[MassMatrix] = {
    j += 1
    if (j < skipFirst || (totalIterations - j) < skipLast)
      None
    else {
      i += 1
      estimator.update(sample)
      if (i == windowSize) {
        i = 0
        windowSize = (windowSize * windowExpansion).toInt
        prevMassMatrix = estimator.massMatrix
        estimator.reset()
        Some(prevMassMatrix)
      } else {
        None
      }
    }
  }
}

class DiagonalMassMatrixTuner(val initialWindowSize: Int,
                              val windowExpansion: Double,
                              val skipFirst: Int,
                              val skipLast: Int)
    extends WindowedMassMatrixTuner {
  def initializeEstimator(size: Int) = new VarianceEstimator(size)
}

class DenseMassMatrixTuner(val initialWindowSize: Int,
                           val windowExpansion: Double,
                           val skipFirst: Int,
                           val skipLast: Int)
    extends WindowedMassMatrixTuner {
  def initializeEstimator(size: Int) = new CovarianceEstimator(size)
}
