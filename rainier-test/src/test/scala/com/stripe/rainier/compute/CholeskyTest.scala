package com.stripe.rainier.compute

import com.stripe.rainier.sampler.RNG

class CholeskyTest extends ComputeTest {
    val rng = RNG.default
    def testRandomSolve(): Unit =  {
        val rank = rng.int(5) + 2
        val y = Vector.fill(rank){rng.standardNormal}
        val packed = Vector.fill(Cholesky.triangleNumber(rank))(rng.standardNormal)
        val realY = y.map(Real(_))
        val realPacked = packed.map(Real(_))
        val realX = Cholesky.lowerTriangularSolve(realPacked, realY)
        val x = realX.collect{case Scalar(d) => d}
        val newY = Cholesky.lowerTriangularMultiply(packed.toArray, x.toArray)
        y.zip(newY).foreach{
            case (yi, newYi) => assertWithinEpsilon(yi, newYi, "")
        }
    }

    test("solve") {
        0.to(10).foreach{_ => testRandomSolve()}
    }
}