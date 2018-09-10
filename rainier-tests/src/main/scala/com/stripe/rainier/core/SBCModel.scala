package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

trait SBCModel {
  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def sbc: SBC[_, _]
  val sampler: Sampler = HMC(1)
  val warmupIterations: Int = 10000
  val syntheticSamples: Int = 1000
  val nSamples: Int = 10
  def main(args: Array[String]): Unit = {
    sbc.animate(sampler, warmupIterations, syntheticSamples)
    println(s"\nnew goldset:")
    println(s"$samples")
    println(
      s"If this run looks good, please update the goldset in your SBCModel")
  }
  val samples: List[Double] = sbc.posteriorSamples(goldset.size)
  def goldset: List[Double]
  val description: String
}

object SBCUniformNormal extends SBCModel {
  def sbc = SBC[Double, Continuous](Uniform(0, 1))((x: Real) => Normal(x, 1))
  override val sampler = HMC(2)
  def goldset =
    List(1.6314534172073512, -0.33686162193999136, 0.9263684306476035,
      0.9196694143409552, 1.1760082627656707, 1.1760082627656707,
      0.5747876547882751, -1.2535122735216744, -0.8359858554305308,
      -0.8359858554305308)

  val description = "Normal(x,1) with Uniform(0,1) prior"
}

object SBCLogNormal extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset =
    List(-0.33956584359261915, -0.16534002778304413, -0.16534002778304413,
      -0.16534002778304413, 0.5145040353754791, 0.5145040353754791,
      -0.08154239293433818, -0.08154239293433818, -0.2926383998760763,
      -0.2835760142365855)
  val description = "LogNormal(x,x) with LogNormal(0,1) prior"
}

/**
  * Note: these are made-up goldsets. SBC on these is wildly slow.
  */
object SBCExponential extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Exponential(x))
  def goldset = List(0.1, 0.33, 1.12)
  val description = "Exponential(x) with LogNormal(0,1) prior"
}

object SBCLaplace extends SBCModel {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Laplace(x, x))
  def goldset = List(0.1, 0.33, 1.12)
  val description = "Laplace(x,x) with LogNormal(0,1) prior"
}
