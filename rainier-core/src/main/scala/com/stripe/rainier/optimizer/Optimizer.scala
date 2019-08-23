package com.stripe.rainier.optimizer

import com.stripe.rainier.sampler.DensityFunction

object Optimizer {
  def lbfgs(df: DensityFunction): Array[Double] = {
    val x = new Array[Double](df.nVars)
    val g = new Array[Double](df.nVars)
    val diag = new Array[Double](df.nVars * df.nVars)
    val iflag = Array(0)
    var complete = false
    while (!complete) {
      df.update(x)
      var i = 0
      while (i < df.nVars) {
        g(i) = df.gradient(i) * -1
        i += 1
      }
      LBFGS.lbfgs(df.nVars,
                  5,
                  x,
                  df.density * -1,
                  g,
                  false,
                  diag,
                  0.1,
                  iflag)
      if (iflag(0) == 0)
        complete = true
    }
    return x
  }
}
