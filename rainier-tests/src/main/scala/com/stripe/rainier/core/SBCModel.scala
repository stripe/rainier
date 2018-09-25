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
  val samples: List[_] = sbc.posteriorSamples(goldset.size)
  def goldset: List[_]
  val description: String
}

/** Continuous **/
object SBCUniformNormal extends SBCModel {
  def sbc = SBC[Double, Continuous](Uniform(0, 1))((x: Real) => Normal(x, 1))
  override val sampler = HMC(2)
  def goldset =
    List(0.2596938696936393, -0.3273257829805834, 0.2729311343019017,
      0.8521704131171517, 0.21151911646461785, -0.7426658572204402,
      2.271325788529349, 0.7786849217450826, 1.2366655909705182,
      0.6982133598813528)
  val description = "Normal(x, 1) with Uniform(0, 1) prior"
}

object SBCLogNormal extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset =
    List(2.349654997391342, 2.8211248182321045, 2.1866557470228996,
      3.1286576255954666, 2.09952942112396, 1.4714940077680554,
      1.9592682404572923, 1.8977626258273104, 3.151026592970336,
      5.754280164073072)
  val description = "LogNormal(x, x) with LogNormal(0, 1) prior"
}

/**
  * Note: SBCExponential and SBCLaplace are made-up goldsets. SBC on these is wildly slow.
  */
object SBCExponential extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Exponential(x))
  def goldset =
    List(1.9444409266417129, 4.884477223783541, 3.166384786757497,
      1.340419862750101, 62.45333008770111, 21.260796478808622,
      2.145416458600115, 0.5978183569453146, 2.745814563529916,
      2.326159027201714)
  val description = "Exponential(x) with LogNormal(0, 1) prior"
}

object SBCLaplace extends SBCModel {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Laplace(x, x))
  def goldset =
    List(1.9444409266417129, 4.884477223783541, 3.166384786757497,
      1.340419862750101, 62.45333008770111, 21.260796478808622,
      2.145416458600115, 0.5978183569453146, 2.745814563529916,
      2.326159027201714)
  val description = "Laplace(x, x) with LogNormal(0, 1) prior"
}

/** Discrete **/
object SBCBernoulli extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Bernoulli(x))
  def goldset =
    List(0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0, 1, 0, 1)
  val description = "Bernoulli(x) with Uniform(0, 1) prior"
}

object SBCBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Binomial(x, 10))
  def goldset =
    List(1, 3, 3, 5, 4, 4, 5, 6, 4, 2, 4, 7, 4, 6, 3, 6, 4, 5, 8, 6, 3)
  val description = "Binomial(x, 10) with Uniform(0, 1) prior"
}

object SBCGeometric extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Geometric(x))
  def goldset =
    List(0, 1, 4, 0, 0, 2, 2, 2, 2, 1, 1, 1, 0, 1, 1, 0, 0, 0, 2, 0, 2)
  val description = "Geometric(x) with Uniform(0, 1) prior"
}

object SBCGeometricZeroInflated extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) =>
      Geometric(.3).zeroInflated(x))
  def goldset =
    List(0, 1, 0, 1, 0, 0, 0, 0, 10, 11, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0)
  val description = "Geometric(.3).zeroInflated(x) with Uniform(0, 1) prior"
}

object SBCNegativeBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => NegativeBinomial(x, 10))
  def goldset =
    List(1, 3, 6, 11, 7, 10, 8, 19, 7, 4, 14, 19, 6, 14, 6, 8, 12, 8, 14, 13, 4)
  val description = "NegativeBinomial(x, 10) with Uniform(0, 1) prior"
}

object SBCBinomialPoissonApproximation extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 0.04))((x: Real) => Binomial(x, 200))
  def goldset =
    List(2, 5, 4, 5, 1, 6, 1, 5, 6, 3, 1, 3, 7, 3, 3, 4, 2, 5, 5, 4, 3)
  val description =
    "Poisson approximation to Binomial: Binomial(x, 200) with Uniform(0, 0.04) prior"
}

object SBCBinomialNormalApproximation extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0.4, 0.6))((x: Real) => Binomial(x, 300))
  def goldset =
    List(1, 6, 10, 234, 10, 3, 4, 9, 8, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3)
  val description =
    "Normal approximation to Binomial: Binomial(x, 200) with Uniform(0.4, 0.6) prior"
}
