package com.stripe.rainier.compute

import com.stripe.rainier.sampler.RNG

class CholeskyTest extends ComputeTest {
  val rng = RNG.default
  def testRandomSolve(): Unit = {
    val rank = rng.int(5) + 2
    val y = Vector.fill(rank) { rng.standardNormal }
    val packed = Vector.fill(Cholesky.triangleNumber(rank))(rng.standardNormal)
    val realY = y.map(Real(_))
    val realPacked = packed.map(Real(_))
    val realX = Cholesky.lowerTriangularSolve(realPacked, realY)
    val x = realX.collect { case Scalar(d) => d }
    val newY = new Array[Double](y.size)
    Cholesky.lowerTriangularMultiply(packed.toArray, x.toArray, newY)
    y.zip(newY).foreach {
      case (yi, newYi) => assertWithinEpsilon(yi, newYi, "")
    }
  }

  def testRandom2x2Inverse(): Unit = {
    val y = Vector.fill(2) { rng.standardNormal }
    val corr = rng.standardUniform
    val diag = Math.sqrt(1.0 - (corr * corr))
    val packed = Vector(1.0, corr, diag)
    val realPacked = packed.map(Real(_))
    val realY = y.map(Real(_))
    val chol = Cholesky(realPacked)
    val realX = chol.inverseMultiply(realY)
    val x = realX.collect { case Scalar(d) => d }
    val newY = Vector(x(0) + corr * x(1), x(0) * corr + x(1))
    assertWithinEpsilon(y(0), newY(0), "y1")
    assertWithinEpsilon(y(1), newY(1), "y2")
  }

  test("lower triangular solve") {
    0.to(10).foreach { _ =>
      testRandomSolve()
    }
  }

  test("2x2 invert multiply") {
    0.to(10).foreach { _ =>
      testRandom2x2Inverse()
    }
  }

  test("upper triangular solve") {
    /*
        L =
           [1 0 0 0]
           [2 3 0 0]
           [4 5 6 0]
           [7 8 9 10]

        L* =
           [1 2 4 7]
           [0 3 5 8]
           [0 0 6 9]
           [0 0 0 10]

        x = [1 2 3 4]

        L*x =
            [1 + 4 + 12 + 28]
            [6 + 15 + 32]
            [18 + 36]
            [40]

            = [45 53 54 40]
     */

    val packed = Vector(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map(Real(_))
    val y = Vector(45, 53, 54, 40).map(Real(_))
    val xReal = Cholesky.upperTriangularSolve(packed, y)
    val x = xReal.collect { case Scalar(d) => d }
    x.zip(Vector(1d, 2d, 3d, 4d)).foreach {
      case (xi, trueXi) => assertWithinEpsilon(xi, trueXi, "")
    }
  }
}

/*
Real(10.0), Real(6.00), Real(9.00), Real(3.00), Real(5.00), Real(8.00), Real(1.00), Real(2.00), Real(4.00), Real(7.00)

[10 0 0 0]
[6 9 0 0]
[3 5 8 0]
[1 2 4 7]

[4 3 2 1]



[40 54 ]
 */
