package com.stripe.rainier.sampler

case class AdaptiveMetric(iterations: Int) extends Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit = {
    state.startPhase("Adapting mass matrix", iterations)

    val n = state.nVars
    val mean = new Array[Double](n)
    val oldDiff = new Array[Double](n)
    val newDiff = new Array[Double](n)
    val cov = 0.until(n).toArray.flatMap { i =>
      val row = Array.fill(n)(0.0)
      row(i) = 1.0
      row
    }

    def diff(buf: Array[Double]): Unit = {
      state.variables(buf)
      var i = 0
      while (i < n) {
        buf(i) -= mean(i)
        i += 1
      }
    }

    var iteration = 0
    while (iteration < iterations) {
      state.step()
      iteration += 1
      diff(oldDiff)
      var i = 0
      while (i < n) {
        mean(i) += (oldDiff(i) / iteration.toDouble)
        i += 1
      }
      diff(newDiff)

      var j = 0
      var l = 0
      while (j < n) {
        var k = 0
        while (k < n) {
          cov(l) += newDiff(j) * oldDiff(k)
          k += 1
          l += 1
        }
        j += 1
      }
    }

    val nn = n * n
    val z = (iteration - 1).toDouble
    var i = 0
    while (i < nn) {
      cov(i) /= z
      i += 1
    }
    state.updateMetric(EuclideanMetric(cov))
  }
}
