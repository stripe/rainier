package com.stripe.rainier.compute

/*
This represents an NxN matrix A
by the  N(N+1)/2 packed lower-triangular elements
of its Cholesky decomposition L
(where A = LL*)
*/

case class Cholesky(packed: Vec[Real]) {
    val n: Int = Cholesky.size(packed)
    
    //log(det(A))
    def logDeterminant: Real = Real.sum(diagonals.map(_.log)) * 2

    //solve for x where Ax=y
    def inverseMultiply(y: Vec[Real]): Vec[Real] = {
        //L*Lx = y, let b=Lx 
        //solve L*b = y
        val b = Cholesky.upperTriangularSolve(packed, y)
        //solve Lx = b
        Cholesky.lowerTriangularSolve(packed, b)
    }

    //diagonal elements of L
    private def diagonals: List[Real] = 
        0.until(n).toList.map{i =>
            packed(Cholesky.triangleNumber(i))
        }
}

object Cholesky {
    def lowerTriangularMultiply(packed: Array[Double], vector: Array[Double]): Array[Double] = {
        val n = vector.size
        val result = new Array[Double](n)
        var i = 0
        var base = 0
        while(i < n) {
            var j = 0
            var acc = 0d
            while(j <= i) {
                acc += vector(j) * packed(base + j)
                j += 1
            }
            result(i) = acc
            i += 1
            base += i                
        }
        result
    }
    
    def triangleNumber(k: Int) = (k * (k+1)) / 2
    def size(packed: Vec[Real]) = Math.sqrt(packed.size * 2.0).floor.toInt

    //solve Lx = y
    def lowerTriangularSolve(packed: Vec[Real], y: Vec[Real]): Vec[Real] = ???

    //solve L*x = y
    def upperTriangularSolve(packed: Vec[Real], y: Vec[Real]): Vec[Real] =
        //let z = y.reverse, and let w = x.reverse
        //"rotate" L to be a lower-triangular matrix R,
        //such that Rw = z implies that  L*x = y
        lowerTriangularSolve(rotate(packed), y.reverse).reverse

    private def rotateIndex(i: Int, j: Int, n: Int): Int =
        triangleNumber(n-i) +
        triangleNumber(n-i+j-1) -
        triangleNumber(n-i-1) -
        1

    private def rotate(packed: Vec[Real]): Vec[Real] = {
        val n = size(packed)
        val result = 0.until(n).toList.flatMap{i => 
            0.to(i).toList.map{j => 
                packed(rotateIndex(i, j, n))
            }
        }
        Vec.from(result)
    }
}