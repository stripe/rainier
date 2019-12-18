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
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
class CategoricalBenchmark {
  implicit val rng: RNG = RNG.default
  implicit val eval: Numeric[Real] = new Evaluator(Map.empty)

  @Benchmark
  def run(): Unit = {
    Categorical
      .list(1.to(100))
      .generator
      .flatMap { i =>
        Categorical.list(1.to(i)).generator
      }
      .get
  }
}
