package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._
import rainier.sampler._

@Warmup(iterations = 1)
@Measurement(iterations = 1)
@Fork(1)
class EndToEnd {
  def normal(k: Int) = {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(k).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)
  }

  @Benchmark
  def fitNormal: Unit = {
    implicit val rng = RNG.default
    Compiler.default = ArrayCompiler
    normal(1000).sample()
  }

  @Benchmark
  def fitNormalAsm: Unit = {
    implicit val rng = RNG.default
    Compiler.default = ASMCompiler
    normal(1000).sample()
  }
}
