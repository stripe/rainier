package com.stripe.rainier.bench.stan

import com.stripe.rainier.core._

//https://github.com/stan-dev/stat_comp_benchmarks/tree/master/benchmarks/eight_schools
class EightSchools extends ModelBenchmark {
  def model = {
    val mu = Normal(0, 5).param
    val tau = Cauchy(0, 5).param.abs
    val thetas = 0.until(sigmas.size).map { _ =>
      Normal(mu, tau).param
    }

    thetas.zip(ys.zip(sigmas)).foldLeft(Model.empty) {
      case (m, (theta, (y, sigma))) =>
        Model
          .observe(y.toDouble, Normal(theta, sigma))
          .merge(m)
    }
  }

  val ys: List[Int] = List(28, 8, -3, 7, -1, 1, 18, 12)
  val sigmas: List[Int] = List(15, 10, 16, 11, 9, 11, 10, 18)
}
