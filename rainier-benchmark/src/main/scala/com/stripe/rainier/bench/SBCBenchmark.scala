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
  @Param(Array("1000", "10000", "100000"))
  protected def syntheticSamples: Int = _

  lazy val s = sbc
  lazy val model = build
  lazy val vars = model.variables
  lazy val df = model.density

  @Benchmark
  def synthesize() = s.synthesize(syntheticSamples)

  @Benchmark
  def build() = s.model(syntheticSamples)

  @Benchmark
  def compile() =
    model.density

  @Benchmark
  def run() =
    df.update(vars.map { _ =>
      rng.standardUniform
    }.toArray)
}

class SBCNormalBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](Uniform(0, 1)) { n =>
      Normal(n, 1)
    }
}

class SBCLaplaceBenchmark extends SBCBenchmark {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1)) { x =>
    Laplace(x, x)
  }
}
