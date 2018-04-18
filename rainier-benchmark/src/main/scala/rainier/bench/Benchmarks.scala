package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._

object Benchmarks {
  trait BenchmarkState {
    val x = new Variable
    def expression: Real

    def setup: (Real,
                Real,
                Array[Double] => Array[Double],
                Array[Double] => Array[Double]) = {
      val expr = expression
      val grad = Gradient.derive(List(x), expr).head
      val cf = ArrayCompiler.compile(List(x), List(expr, grad))
      val asm = ASMCompiler.compile(List(x), List(expr, grad))
      (expr, grad, cf, asm)
    }

    val (expr, grad, cf, asm) = setup

    Real.prune = false
    val (_, _, unpruned, _) = setup
    Real.prune = true
    Real.intern = false
    val (_, _, uninterned, _) = setup
    Real.intern = true

    val rand = new scala.util.Random
    def runCompiled = cf(Array(rand.nextDouble))
    def runAsm = asm(Array(rand.nextDouble))
    def runUnpruned = unpruned(Array(rand.nextDouble))
    def runUninterned = uninterned(Array(rand.nextDouble))
    def evaluate = {
      val eval = new Evaluator(Map(x -> rand.nextDouble))
      eval.toDouble(expr) + eval.toDouble(grad)
    }

    def compile = ArrayCompiler.compile(List(x), List(expr, grad))
    def compileAsm = ASMCompiler.compile(List(x), List(expr, grad))
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
  def runNormalAsm(state: NormalBenchmark): Unit = {
    state.runAsm
  }
  
  @Benchmark
  def compileNormal(state: NormalBenchmark): Unit = {
    state.compile
  }

  @Benchmark
  def compileNormalAsm(state: NormalBenchmark): Unit = {
    state.compileAsm
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

  @Benchmark
  def compilePoisson(state: PoissonBenchmark): Unit = {
    state.compile
  }

  @Benchmark
  def compilePoissonAsm(state: PoissonBenchmark): Unit = {
    state.compileAsm
  }
}
