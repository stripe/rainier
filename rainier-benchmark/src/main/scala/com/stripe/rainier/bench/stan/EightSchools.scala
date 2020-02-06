package com.stripe.rainier.bench.stan

import com.stripe.rainier.core._

//https://github.com/stan-dev/stat_comp_benchmarks/tree/master/benchmarks/eight_schools
//Stan: Gradient evaluation took 2.4e-05 seconds
//JMH: 1.048 ± 0.011  us/op
class EightSchools extends ModelBenchmark {
  def model = {
    val mu = Normal(0, 5).latent
    val tau = Cauchy(0, 5).latent.abs
    val thetas = Normal(mu, tau).latentVec(sigmas.size)

    thetas.toList.zip(ys.zip(sigmas)).foldLeft(Model.empty) {
      case (m, (theta, (y, sigma))) =>
        Model
          .observe(y.toDouble, Normal(theta, sigma))
          .merge(m)
    }
  }

  val ys: List[Int] = List(28, 8, -3, 7, -1, 1, 18, 12)
  val sigmas: List[Int] = List(15, 10, 16, 11, 9, 11, 10, 18)
}
