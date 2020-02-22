package com.stripe.rainier.compute

/*
This represents an NxN matrix A
by the  N(N+1)/2 packed lower-triangular elements
of its Cholesky decomposition L
(where A = LL*)
 */
case class Cholesky(packed: Vector[Real]) {
  val size: Int = Cholesky.dimension(packed.size)

  //log(det(A))
  val logDeterminant: Real = Real.sum(diagonals.map(_.log)) * 2

  //solve for x where Ax=y
  def inverseMultiply(y: Vector[Real]): Vector[Real] = {
    //LL*x = y, let b=Lx
    //solve Lb = y
    val b = Cholesky.lowerTriangularSolve(packed, y)
    //solve L*x = b
    Cholesky.upperTriangularSolve(packed, b)
  }

  //Lx
  def lowerTriangularMultiply(vector: Vector[Real]): Vector[Real] =
    0.until(vector.size).toVector.map { i =>
      val base = Cholesky.triangleNumber(i)
      Real.sum(0.to(i).map { j =>
        vector(j) * packed(base + j)
      })
    }

  //diagonal elements of L
  def diagonals: Vector[Real] =
    0.until(size).toVector.map { i =>
      packed(Cholesky.triangleNumber(i + 1) - 1)
    }
}

object Cholesky {
  //number of non-zero elements in the first k rows of a triangular matrix
  def triangleNumber(k: Int): Int = (k * (k + 1)) / 2

  def lowerTriangularMultiply(packed: Array[Double],
                              vector: Array[Double],
                              result: Array[Double]): Unit = {
    val n = result.size
    var i = 0
    var base = 0
    while (i < n) {
      var j = 0
      var acc = 0d
      while (j <= i) {
        acc += vector(j) * packed(base + j)
        j += 1
      }
      result(i) = acc
      i += 1
      base += i
    }
  }

  private def packedIndex(i: Int, j: Int): Int =
    triangleNumber(i) + j

  def dimension(packedSize: Int) = Math.sqrt(packedSize * 2.0).floor.toInt

  //solve Lx = y
  def lowerTriangularSolve(packed: Vector[Real],
                           y: Vector[Real]): Vector[Real] =
    //forward substitution
    y.toList.zipWithIndex.foldLeft(Vector.empty[Real]) {
      case (xAcc, (yi, i)) =>
        val row = packed.slice(triangleNumber(i), triangleNumber(i + 1))
        val dot = Real.sum(row.init.zip(xAcc).map { case (a, b) => a * b })
        val xi = (yi - dot) / row.last
        xAcc :+ xi
    }

  //solve L*x = y
  def upperTriangularSolve(packed: Vector[Real],
                           y: Vector[Real]): Vector[Real] =
    //let z = y.reverse, and let w = x.reverse
    //"reflect" L to be a lower-triangular matrix R,
    //such that Rw = z implies that  L*x = y
    lowerTriangularSolve(reflect(packed), y.reverse).reverse

  //the reflection of m[i,j] is m[n-j-1,n-i-1]
  private def reflect(packed: Vector[Real]): Vector[Real] = {
    val n = dimension(packed.size)
    0.until(n)
      .flatMap { i =>
        0.to(i).map { j =>
          packed(packedIndex(n - j - 1, n - i - 1))
        }
      }
      .toVector
  }
}
