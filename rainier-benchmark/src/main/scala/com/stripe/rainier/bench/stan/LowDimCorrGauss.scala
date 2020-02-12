package com.stripe.rainier.bench.stan

import com.stripe.rainier.core._

//https://github.com/stan-dev/stat_comp_benchmarks/blob/master/benchmarks/low_dim_corr_gauss
//JMH: 10⁻⁶ s/op
//Stan: Gradient evaluation took 3.5e-05 seconds
class LowDimCorrGauss extends ModelBenchmark {
  def model = {
    val (z1, z2) = BivariateNormal((0, 3), (1, 2), 0.5).latent
    Model.track(Set(z1, z2))
  }
}
