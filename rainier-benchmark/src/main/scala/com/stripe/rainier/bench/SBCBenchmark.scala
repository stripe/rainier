package com.stripe.rainier.bench

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(4)
@State(Scope.Benchmark)
abstract class SBCBenchmark {
  implicit val rng: RNG = RNG.default

  protected def sbc: SBC[_, _]
  protected def syntheticSamples: Int = 1000
  protected def batches: Int = 1

  val s = sbc
  val model = build
  val context = model.context
  val vars = context.variables
  val cf = compile
  val inlined = context.density
  val inlinecf = compileInlined

  @Benchmark
  def synthesize = s.synthesize(syntheticSamples)

  @Benchmark
  def build = s.model(syntheticSamples)

  @Benchmark
  def compile =
    context.compileDensity

  @Benchmark
  def inline = model.context.density

  @Benchmark
  def compileInlined =
    context.compiler.compile(vars, inlined)

  @Benchmark
  def run =
    cf(vars.map { _ =>
      rng.standardUniform
    }.toArray)

  @Benchmark
  def runInlined =
    inlinecf(vars.map { _ =>
      rng.standardUniform
    }.toArray)
}

class SBCNormalBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](Uniform(0, 1)) { n =>
      Normal(n, 1)
    }
}

class SBCNormalBenchmark100k extends SBCNormalBenchmark {
  override def syntheticSamples = 100000
  override def batches = 100
}

class SBCLaplaceBenchmark extends SBCBenchmark {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1)) { x =>
    Laplace(x, x)
  }
}

class SBCLaplaceBenchmark100k extends SBCLaplaceBenchmark {
  override def syntheticSamples = 100000
  override def batches = 100
}
