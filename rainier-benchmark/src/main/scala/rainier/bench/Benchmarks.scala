package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._

object Benchmarks {
  trait BenchmarkState {
    val x = new Variable
    def expression: Real
    val cachedExpression = expression
    val compiled = Compiler(List(cachedExpression))
    val gradient = Gradient.derive(List(x), cachedExpression)
    val compiledGradient = Compiler(gradient)

    def runCompiled = compiled(Map(x -> 0.0))
    def evaluate = new Evaluator(Map(x -> 0.0)).toDouble(cachedExpression)
  }

  @State(Scope.Benchmark)
  class NormalBenchmark extends BenchmarkState {
    def expression = Normal(x, 1).logDensities(0d.to(2d).by(0.002).toList)
  }
}

class Benchmarks {
  import Benchmarks._

  @Benchmark
  def runNormal(state: NormalBenchmark): Unit = {
    state.runCompiled
  }

  @Benchmark
  def evaluateNormal(state: NormalBenchmark): Unit = {
    state.evaluate
  }

}
