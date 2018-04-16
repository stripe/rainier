package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._

object Benchmarks {
  trait BenchmarkState {
    val x = new Variable
    def expression: Real

    def compile: (Real, Real, Compiler.CompiledFunction, Double => Double) = {
      val expr = expression
      val grad = Gradient.derive(List(x), expr).head
      val cf = Compiler(List(expr, grad))
      val asm = ASM.compileToFunction(expr + grad)
      (expr, grad, cf, asm)
    }

    val (expr, grad, cf, asm) = compile

    Real.prune = false
    val (_, _, unpruned, _) = compile
    Real.prune = true
    Real.intern = false
    val (_, _, uninterned, _) = compile
    Real.intern = true

    def runCompiled = cf(Map(x -> 1.0))
    def runAsm = asm(1.0)
    def runUnpruned = unpruned(Map(x -> 1.0))
    def runUninterned = uninterned(Map(x -> 1.0))
    def evaluate = {
      val eval = new Evaluator(Map(x -> 1.0))
      eval.toDouble(expr) + eval.toDouble(grad)
    }
  }

  @State(Scope.Benchmark)
  class NormalBenchmark extends BenchmarkState {
    def expression = Normal(x, 1).logDensities(0d.to(2d).by(0.1).toList)
  }

  @State(Scope.Benchmark)
  class PoissonBenchmark extends BenchmarkState {
    def expression = Poisson(x).logDensities(0.to(10).toList)
  }
}

@Warmup(iterations = 4)
@Measurement(iterations = 10)
@Fork(3)
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

  @Benchmark
  def runNormalUnpruned(state: NormalBenchmark): Unit = {
    state.runUnpruned
  }

  @Benchmark
  def runNormalUninterned(state: NormalBenchmark): Unit = {
    state.runUninterned
  }

  @Benchmark
  def runPoisson(state: PoissonBenchmark): Unit = {
    state.runCompiled
  }

  @Benchmark
  def runPoissonAsm(state: PoissonBenchmark): Unit = {
    state.runAsm
  }

  @Benchmark
  def evaluatePoisson(state: PoissonBenchmark): Unit = {
    state.evaluate
  }

  @Benchmark
  def runPoissonUnpruned(state: PoissonBenchmark): Unit = {
    state.runUnpruned
  }

  @Benchmark
  def runPoissonUninterned(state: PoissonBenchmark): Unit = {
    state.runUninterned
  }
}
