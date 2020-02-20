package com.stripe.rainier

object Utils {
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
}
