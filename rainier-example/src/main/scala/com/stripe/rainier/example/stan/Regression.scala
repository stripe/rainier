package com.stripe.rainier.example.stan

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

/**
From an email from Sean Talts suggesting the following model:

data {
  int N;
  int K;
  matrix[N, K] X;
  vector[N] y;
}
parameters {
  vector[K] beta;
  real<lower=0> sigma;
}
model {
  beta ~ normal(0, 1);
  y ~ normal(X * beta, sigma);
}

with data generated in R via

N = 10000
K = 2000
beta = rnorm(K, 0, 2)
X = matrix(rnorm(N * K, 0, 5), nrow=N)
sigma = 3
y = rnorm(N, X * beta, sigma)
dump(c('N', 'K', 'X','y'),file="regr.data.R"))
**/
object Regression {
  def model(data: Seq[(Seq[Double], Double)]): RandomVariable[Real] = {
    val k = data.head._1.size
    for {
      betas <- RandomVariable.fill(k)(Normal(0, 1).param)
      sigma <- Uniform(0, 10).param
      _ <- Predictor
        .fromDoubleVector { vec =>
          val mean = Real.dot(betas, vec)
          Normal(mean, sigma)
        }
        .fit(data)
    } yield sigma
  }

  def main(args: Array[String]): Unit = {
    val m = model(synthesize(10000, 4, 3.0))
    val t1 = System.nanoTime
    val s = m.sample(HMC(5), 1000, 20000)
    val t2 = System.nanoTime
    println("seconds: " + ((t2 - t1) / 1e9))
    plot1D(s)
  }

  def synthesize(n: Int, k: Int, sigma: Double): Seq[(Seq[Double], Double)] = {
    val r = new scala.util.Random
    val betas = List.fill(k)(r.nextGaussian * 2)
    List.fill(n) {
      val cov = List.fill(k)(r.nextGaussian * 5)
      val ymean = cov.zip(betas).map { case (x, y) => x * y }.sum
      val y = (r.nextGaussian * sigma) + ymean
      (cov, y)
    }
  }
}
