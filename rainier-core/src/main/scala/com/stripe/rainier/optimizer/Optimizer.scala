package com.stripe.rainier.optimizer

import com.stripe.rainier.sampler.DensityFunction

object Optimizer {
  def lbfgs(df: DensityFunction): Array[Double] = {
    val x = new Array[Double](df.nVars)
    val g = new Array[Double](df.nVars)

    //for LBFGSOrig
    val x2 = new Array[Double](df.nVars)
    val diag = new Array[Double](df.nVars)
    val iflag = Array(0)

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

      LBFGSOrig.lbfgs(x2.size,
                      m,
                      x2,
                      df.density * -1,
                      g,
                      false,
                      diag,
                      eps,
                      iflag)
      assert(x.toList == x2.toList)
    }
    return x
  }
}
