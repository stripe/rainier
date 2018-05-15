package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._
import rainier.sampler._

object Benchmarks {
  trait BenchmarkState {
    def expression: Real
    val expr = expression
    val vars = expr.variables

    val a = compileAsm
    val g = Compiler.default.compileGradient(vars, expr)

    implicit val rng = RNG.default
    def runAsm =
      a(vars.map { _ =>
        rng.standardUniform
      }.toArray)
    def runGradient =
      a(vars.map { _ =>
        rng.standardUniform
      }.toArray)
    def compileAsm = Compiler.default.compile(vars, expr)
    def sampleHMC(steps: Int) =
      HMC(steps).sample(expr, 1000).take(10000).toList
    def sampleWalkers(walkers: Int) =
      Walkers(walkers).sample(expr, 1000).take(10000).toList
    def endToEndHMC(steps: Int) = {
      val d = expression
      RandomVariable(d, d).sample(HMC(steps), 1000, 10000)
    }
    def endToEndWalkers(walkers: Int) = {
      val d = expression
      RandomVariable(d, d).sample(Walkers(walkers), 1000, 10000)
    }
  }

  @State(Scope.Benchmark)
  class NormalBenchmark extends BenchmarkState {
    def expression = {
      val x = new Variable
      Normal(x, 1).logDensities(0d.to(2d).by(0.001).toList)
    }
  }

  @State(Scope.Benchmark)
  class PoissonBenchmark extends BenchmarkState {
    def expression = {
      val x = new Variable
      Poisson(x).logDensities(0.to(10).toList)
    }
  }

  @State(Scope.Benchmark)
  class FullNormalBenchmark extends BenchmarkState {
    def expression = {
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
    def expression = {
      val data =
        List(false, true, false, false, false, false, false, false, false, true)

      val model = for {
        theta <- Uniform.standard.param
        _ <- Categorical.boolean(theta).fit(data)
      } yield theta

      model.density
    }
  }
}

@Warmup(iterations = 3)
@Measurement(iterations = 10)
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

  @Benchmark
  def runBernoulliGradient(state: BernoulliBenchmark): Unit = {
    state.runGradient
  }

  @Benchmark
  def endToEndBernoulli(state: BernoulliBenchmark): Unit = {
    state.endToEndHMC(5)
  }
}
