package rainier.example

import rainier.compute._
import rainier.core._
import rainier.sampler._

object FitHLL {
  val hll = HLL(10)
  val rand = new scala.util.Random
  implicit val rng = RNG.default

  def model(sketch: Map[Int, Byte]) =
    NonNegative.param.condition { lambda =>
      hll.logDensity(lambda, sketch)
    }

  def compare(scale: Int): RandomVariable[Real] = {
    println("Generating a set with max size " + scale)
    val data = 1.to(scale).map { i =>
      rand.nextInt
    }
    println("True size: " + data.toSet.size)

    val sketch = hll(data)
    println("Estimated size: " + hll.cardinality(sketch).toInt)
    val (lower, upper) = hll.bounds(sketch)
    println("Confidence interval: " + lower.toInt + ", " + upper.toInt)

    println("Inferring size")
    val m = model(sketch)
    val t1 = System.currentTimeMillis
    val samples =
      m.sample(Hamiltonian(10000, 1000, 100, 100, SampleHMC, 1, 0.01))
    val t2 = System.currentTimeMillis
    val mean = samples.sum / samples.size
    println("Inferred size: " + mean.toInt)
    val sorted = samples.sorted
    val lower2 = sorted((samples.size * 0.05).toInt)
    val upper2 = sorted((samples.size * 0.95).toInt)
    println("Credible interval: " + lower2.toInt + ", " + upper2.toInt)
    println("ms: " + (t2 - t1))
    println("")
    m
  }

  def main(args: Array[String]) {
    compare(1000)
  }
}

object TraceHLL {
  def main(args: Array[String]) {
    val m = FitHLL.compare(1000)
    val d = m.density
    val v = Real.variables(d).toList.head
    val g = Gradient.derive(List(v), d).head
    val c = Compiler(List(d, g))
    c.trace
  }
}
