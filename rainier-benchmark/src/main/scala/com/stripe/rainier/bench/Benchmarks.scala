package com.stripe.rainier.bench

import org.openjdk.jmh.annotations._

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

object Benchmarks {
  trait BenchmarkState {
    def expression: Real
    val expr: Real = expression
    val vars: Seq[Variable] =
      expr.variables

    val a: Array[Double] => Double =
      compileAsm
    val g: Array[Double] => (Double, Array[Double]) =
      Compiler.default.compileGradient(vars, expr)

    implicit val rng: RNG = RNG.default
    def runAsm: Double =
      a(vars.map { _ =>
        rng.standardUniform
      }.toArray)
    def runGradient: Double =
      a(vars.map { _ =>
        rng.standardUniform
      }.toArray)
    def compileAsm: Array[Double] => Double =
      Compiler.default.compile(vars, expr)
    def sampleHMC(steps: Int): List[Sample] =
      HMC(steps).sample(expr, 1000).take(10000).toList
    def sampleWalkers(walkers: Int): List[Sample] =
      Walkers(walkers).sample(expr, 1000).take(10000).toList
    def endToEndHMC(steps: Int): List[Double] = {
      val d = expression
      RandomVariable(d, d).sample(HMC(steps), 1000, 10000)
    }
    def endToEndWalkers(walkers: Int): List[Double] = {
      val d = expression
      RandomVariable(d, d).sample(Walkers(walkers), 1000, 10000)
    }
  }

  @State(Scope.Benchmark)
  class NormalBenchmark extends BenchmarkState {
    def expression: Real = {
      val x = new Variable
      Normal(x, 1).logDensities(
        Range.BigDecimal(0d, 2d, 0.001d).map(_.toDouble).toList)
    }
  }

  @State(Scope.Benchmark)
  class PoissonBenchmark extends BenchmarkState {
    def expression: Real = {
      val x = new Variable
      Poisson(x).logDensities(0.to(10).toList)
    }
  }

  @State(Scope.Benchmark)
  class FullNormalBenchmark extends BenchmarkState {
    def expression: Real = {
      val r = new scala.util.Random
      val trueMean = 3.0
      val trueStddev = 2.0
      val data = 1.to(1000).map { i =>
        (r.nextGaussian * trueStddev) + trueMean
      }

      val model = for {
        mean <- Uniform(0, 10).param
        stddev <- Uniform(0, 10).param
        _ <- Normal(mean, stddev).fit(data)
      } yield (mean, stddev)

      model.density
    }
  }

  @State(Scope.Benchmark)
  class BernoulliBenchmark extends BenchmarkState {
    def expression: Real = {
      val data =
        List(false, true, false, false, false, false, false, false, false, true)

      val model = for {
        theta <- Uniform.standard.param
        _ <- Categorical.boolean(theta).fit(data)
      } yield theta

      model.density
    }
  }

  @State(Scope.Benchmark)
  class FunnelBenchmark extends BenchmarkState {
    def expression: Real = {
      val model =
        for {
          y <- Normal(0, 3).param
          x <- RandomVariable.traverse(1.to(9).map { _ =>
            Normal(0, (y / 2).exp).param
          })
        } yield (x(0), y)

      model.density
    }
  }
}

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
class Benchmarks {
  import Benchmarks._

  @Benchmark
  def runFullNormalAsm(state: FullNormalBenchmark): Unit = {
    state.runAsm
  }

  @Benchmark
  def compileFullNormalAsm(state: FullNormalBenchmark): Unit = {
    state.compileAsm
  }

  @Benchmark
  def runNormalAsm(state: NormalBenchmark): Unit = {
    state.runAsm
  }

  @Benchmark
  def runNormalGradient(state: NormalBenchmark): Unit = {
    state.runGradient
  }

  @Benchmark
  def compileNormalAsm(state: NormalBenchmark): Unit = {
    state.compileAsm
  }

  @Benchmark
  def runPoissonAsm(state: PoissonBenchmark): Unit = {
    state.runAsm
  }

  @Benchmark
  def compilePoissonAsm(state: PoissonBenchmark): Unit = {
    state.compileAsm
  }

  @Benchmark
  def sampleNormalWalkers(state: NormalBenchmark): Unit = {
    state.sampleWalkers(100)
  }

  @Benchmark
  def sampleNormalHMC(state: NormalBenchmark): Unit = {
    state.sampleHMC(5)
  }

  @Benchmark
  def endToEndNormalWalkers(state: NormalBenchmark): Unit = {
    state.endToEndWalkers(100)
  }

  @Benchmark
  def endToEndNormalHMC(state: NormalBenchmark): Unit = {
    state.endToEndHMC(5)
  }

  //Stan runs this at about 60,000 ops/sec
  //vs our 5,000,000 ops/sec
  @Benchmark
  def runBernoulliGradient(state: BernoulliBenchmark): Unit = {
    state.runGradient
  }

  //Stan runs this at about 4 ops/sec
  //vs our 35 ops/sec
  @Benchmark
  def endToEndBernoulli(state: BernoulliBenchmark): Unit = {
    state.endToEndHMC(5)
  }

  //Stan runs this at about 50,000 ops/sec
  //vs our 1,600,000 ops/sec
  @Benchmark
  def runFunnelGradient(state: FunnelBenchmark): Unit = {
    state.runGradient
  }

  //Stan runs this at about 2 ops/sec
  //vs our 20 ops/sec
  @Benchmark
  def endToEndFunnel(state: FunnelBenchmark): Unit = {
    state.endToEndHMC(5)
  }
}
