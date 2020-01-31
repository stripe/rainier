package com.stripe.rainier.optimizer

import com.stripe.rainier.sampler.DensityFunction

object Optimizer {
  def lbfgs(df: DensityFunction): Array[Double] = {
    val x = new Array[Double](df.nVars)
    val g = new Array[Double](df.nVars)

    var complete = false
    val m = 5
    val eps = 0.1
    val lb = new LBFGS(x, m, eps)
    while (!complete) {
      df.update(x)
      var i = 0
      while (i < df.nVars) {
        g(i) = df.gradient(i) * -1
        i += 1
      }
      complete = lb(df.density * -1, g)
    }
    return x
  }
}
