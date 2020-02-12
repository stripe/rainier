package com.stripe.rainier.core

import org.scalatest.FunSuite
import com.stripe.rainier.sampler._

class MVTest extends FunSuite {
//per https://github.com/stan-dev/stat_comp_benchmarks/blob/master/benchmarks/low_dim_corr_gauss/low_dim_corr_gauss.stan
  test("bivariate normal") {
    val mu1 = 0
    val mu2 = 3
    val sd1 = 1
    val sd2 = 2
    val rho = 0.5
    val z = BivariateNormal((mu1, mu2), (sd1, sd2), rho).latent
    val samples = Model.sample(z, HMC(1000, 100000, 5))

    assertMeanZero(samples.map {
      case (z1, _) =>
        Math.pow(z1 - mu1, 2) - (sd1 * sd1)
    }, "delta1")

    assertMeanZero(samples.map {
      case (_, z2) =>
        Math.pow(z2 - mu2, 2) - (sd2 * sd2)
    }, "delta2")

    assertMeanZero(samples.map {
      case (z1, z2) =>
        (z1 - mu1) * (z2 - mu2) / (sd1 * sd2) - rho
    }, "deltaCorr")
  }

  def assertMeanZero(seq: Seq[Double], clue: String): Unit = {
    assert(Math.abs(seq.sum / seq.size) < 0.15, clue)
    ()
  }
}
