package com.stripe.rainier.optimizer

import com.stripe.rainier.sampler.DensityFunction

object Optimizer {
  def lbfgs(df: DensityFunction): Array[Double] = {
    val x = new Array[Double](df.nVars)
    val g = new Array[Double](df.nVars)
    val iflag = Array(0)
    var complete = false
    val lb = new LBFGS(x, 5, 0.1)
    while (!complete) {
      df.update(x)
      var i = 0
      while (i < df.nVars) {
        g(i) = df.gradient(i) * -1
        i += 1
      }
      lb(df.density * -1, g, iflag)
      if (iflag(0) == 0)
        complete = true
      else if (iflag(0) < 0)
        sys.error("LBFGS failure")
    }
    return x
  }
}
