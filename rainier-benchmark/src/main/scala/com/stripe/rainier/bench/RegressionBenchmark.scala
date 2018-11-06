package com.stripe.rainier.bench

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(4)
@State(Scope.Benchmark)
class RegressionBenchmark {
  implicit val rng: RNG = RNG.default

  @Param(Array("10", "10000"))
  protected var n: Int = _

  @Param(Array("1", "10", "100"))
  protected var k: Int = _

  lazy val data = synthesize
  lazy val model = build
  lazy val vars = model.targetGroup.variables
  lazy val df = model.density

  def synthesize() = {
    val betas = List.fill(k)(rng.standardNormal * 2)
    val sigma = 3.0
    List.fill(n) {
      val cov = List.fill(k)(rng.standardNormal * 5)
      val ymean = cov.zip(betas).map { case (x, y) => x * y }.sum
      val y = (rng.standardNormal * sigma) + ymean
      (cov, y)
    }
  }

  @Benchmark
  def build() =
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

  @Benchmark
  def compile() =
    model.density

  @Benchmark
  def run() =
    df.update(vars.map { _ =>
      rng.standardUniform
    }.toArray)
}

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
