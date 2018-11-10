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
abstract class SBCBenchmark {
  implicit val rng: RNG = RNG.default

  protected def sbc: SBC[_, _]

  @Param(Array("100", "1000", "10000", "100000"))
  protected var syntheticSamples: Int = _

  var s: SBC[_, _] = _
  var model: RandomVariable[_] = _
  var df: DensityFunction = _

  @Setup(Level.Trial)
  def setup() = {
    s = sbc
    model = build
    df = model.density
  }

  @Benchmark
  def synthesize() = s.synthesize(syntheticSamples)

  @Benchmark
  def build() = s.model(syntheticSamples)

  @Benchmark
  def compile() =
    Compiler.default.compileTargets(TargetGroup(model.targets), true, 4)

  @Benchmark
  def run() =
    df.update(Array.fill(df.nVars){rng.standardUniform})
}

class NormalBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](Uniform(0, 1)) { n =>
      Normal(n, 1)
    }
}

class LaplaceBenchmark extends SBCBenchmark {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1)) { x =>
    Laplace(x, x)
  }
}

class LogNormalBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => LogNormal(x, x))
}

class ExponentialBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Exponential(x))
}

class BernoulliBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Bernoulli(x))
}

class BinomialBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Binomial(x, 10))
}

class GeometricBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Geometric(x))
}

class GeometricZeroInflatedBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) =>
      Geometric(.3).zeroInflated(x))
}

class NegativeBinomialBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => NegativeBinomial(x, 10))
}

class BinomialPoissonApproximationBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 0.04))((x: Real) => Binomial(x, 200))
}

class GaussianMixtureBenchmark extends SBCBenchmark {
  def sbc =
    SBC[Double, Continuous](Uniform(0, 1))(
      (x: Real) =>
        Mixture(
          Map(
            Normal(0, 1) -> x,
            Normal(1, 2) -> (Real.one - x)
          )
      ))
}
