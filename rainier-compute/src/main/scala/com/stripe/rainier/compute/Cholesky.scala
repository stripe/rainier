package com.stripe.rainier.compute

case class Cholesky(packed: Vec[Real]) {
    def determinant: Real = ???
    def inverseMultiply(vector: Vec[Real]): Vec[Real] = ???
    def lowerTriangularMultiply(vector: Vec[Real]): Vec[Real] = {
        var base = 0
        val results = 0.until(vector.size).toList.map{i =>
            val x = Real.sum(0.to(i).map{j => 
                vector(j) * packed(base + j)
            })
            base += i
            x
        }
        Vec.from(results)
    }
}

object Cholesky {
    def lowerTriangularMultiply(cholesky: Array[Double], vector: Array[Double]): Array[Double] = {
        val n = vector.size
        val result = new Array[Double](n)
        var i = 0
        var base = 0
        while(i < n) {
            var j = 0
            var acc = 0d
            while(j <= i) {
                acc += vector(j) * cholesky(base + j)
                j += 1
            }
            result(i) = acc
            i += 1
            base += i                
        }
        result
    }
}