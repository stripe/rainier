package rainier.bench

import org.openjdk.jmh.annotations._

import rainier.compute._
import rainier.core._
import rainier.sampler._

@Warmup(iterations = 1)
@Measurement(iterations = 1)
@Fork(1)
class EndToEnd {
  def normal(k: Int) = {}

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
