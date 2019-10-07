package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

object FitHLL {
  val hll: HLL = HLL(10)
  val rand: scala.util.Random = new scala.util.Random
  implicit val rng: RNG = RNG.default

  def model(sketch: Map[Int, Byte]): RandomVariable[Real] =
    LogNormal(0, 1).param.condition { lambda =>
      hll.logDensity(lambda, sketch)
    }

  def compare(scale: Int): RandomVariable[Real] = {
    println(s"Generating a set with max size $scale")
    val data = 1.to(scale).map { _ =>
      rand.nextInt
    }
    println(s"True size: ${data.toSet.size}")

    val sketch = hll(data)
    println(s"Estimated size: ${hll.cardinality(sketch).toInt}")
    val (lower, upper) = hll.bounds(sketch)
    println(s"Confidence interval: ${lower.toInt}, ${upper.toInt}")

    println("Inferring size")
    val m = model(sketch)
    val t1 = System.currentTimeMillis
    val samples = m.sample()
    val t2 = System.currentTimeMillis
    val mean = samples.sum / samples.size
    println(s"Inferred size: ${mean.toInt}")
    val sorted = samples.sorted
    val lower2 = sorted((samples.size * 0.05).toInt)
    val upper2 = sorted((samples.size * 0.95).toInt)
    println(s"Credible interval: ${lower2.toInt}, ${upper2.toInt}")
    println(s"ms: ${(t2 - t1)}")
    println("")
    m
  }

  def main(args: Array[String]): Unit = {
    compare(1000)
    ()
  }
}
