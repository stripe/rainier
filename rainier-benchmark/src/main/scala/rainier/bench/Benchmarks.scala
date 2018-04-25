package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.compute.compiler._
import rainier.core._

object Benchmarks {
  trait BenchmarkState {
    def expression: Real
    val expr = expression
    val vars = expr.variables

    def compile = Compiler.compile(vars, expr)

    val cf = compile

    val rand = new scala.util.Random
    def runCompiled =
      cf(vars.map { _ =>
        rand.nextDouble
      }.toArray)
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
}

@Warmup(iterations = 4)
@Measurement(iterations = 10)
@Fork(3)
class Benchmarks {
  import Benchmarks._

  @Benchmark
  def runFullNormal(state: FullNormalBenchmark): Unit = {
    state.runCompiled
  }

  @Benchmark
  def runNormal(state: NormalBenchmark): Unit = {
    state.runCompiled
  }

  @Benchmark
  def compileNormal(state: NormalBenchmark): Unit = {
    state.compile
  }

  @Benchmark
  def runPoisson(state: PoissonBenchmark): Unit = {
    state.runCompiled
  }

  @Benchmark
  def compilePoisson(state: PoissonBenchmark): Unit = {
    state.compile
  }
}
