package com.stripe.rainier.bench.stan

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

import com.stripe.rainier.core._
import com.stripe.rainier.sampler.{RNG, DensityFunction}

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
abstract class ModelBenchmark {
  implicit val rng: RNG = RNG.default

  protected def model: Model
  var df: DensityFunction = _
  var params: Array[Double] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    df = model.density
    params = Array.fill(df.nVars) { rng.standardUniform }
  }

  @Benchmark
  def run(): Unit =
    df.update(params)

  @Benchmark
  def build(): Unit =
    model.density

  def main(): Unit = {
    com.stripe.rainier.compute.Log.showFine()
    println("Running " + this.getClass.getName)
    setup()
    run()
  }
}

object ModelBenchmarks {
  def main(args: Array[String]): Unit = {
    new ARK().main()
    new EightSchools().main()
    new LowDimGaussMix().main()
    new GLMMPoisson2().main()
  }
}
