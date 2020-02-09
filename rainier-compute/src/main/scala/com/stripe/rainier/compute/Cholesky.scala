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
    //L*Lx = y, let b=Lx
    //solve L*b = y
    val b = Cholesky.upperTriangularSolve(packed, y)
    //solve Lx = b
    Cholesky.lowerTriangularSolve(packed, b)
  }

  //Lx
  def lowerTriangularMultiply(x: Vector[Real]): Vector[Real] = ???
  
  //diagonal elements of L
  private def diagonals: Vector[Real] =
    0.until(size).toVector.map { i =>
      packed(Cholesky.triangleNumber(i))
    }
}

object Cholesky {
  def lowerTriangularMultiply(packed: Array[Double],
                              vector: Array[Double]): Array[Double] = {
    val n = vector.size
    val result = new Array[Double](n)
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
    result
  }

  //number of non-zero elements in the first k rows of a triangular matrix
  def triangleNumber(k: Int): Int = (k * (k + 1)) / 2

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
