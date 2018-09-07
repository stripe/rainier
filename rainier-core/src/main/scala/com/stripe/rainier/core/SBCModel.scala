package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

abstract class SBCModel[T, L <: Distribution[T]] {
  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def sbc: SBC[T, L]
  def sampler: Sampler = HMC(1)
  def warmupIterations: Int = 10000
  def syntheticSamples: Int = 1000
  def nSamples: Int = 10
  def main(args: Array[String]): Unit = {
    val newGoldset = samples
    sbc.animate(sampler, warmupIterations, syntheticSamples)
    println(s"\nnew goldset:")
    println(s"$newGoldset")
    println(
      s"If this run looks good, please update the goldset in your SBCModel")
  }
  def samples: List[T] = sbc.posteriorSamples(goldset.size)
  def goldset: List[T]
  def description: String
}

object SBCUniformNormal extends SBCModel[Double, Continuous] {
  def sbc = SBC(Uniform(0, 1))((x: Real) => Normal(x, 1))
  override def sampler = HMC(2)
  def goldset =
    List(0.770217434378013, 1.2877991075049935, 1.0761575302777342,
      0.06063426627950119, 2.235772959187611, 1.5916139704386643,
      0.4680530806057483, -1.3360844052727425, 0.6558428534558145,
      0.4233930395976211)
  def description = "Normal(x,1) with Uniform(0,1) prior"
}

object SBCLogNormal extends SBCModel[Double, Continuous] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset: List[Double] =
    List(1.9444409266417129, 4.884477223783541, 3.166384786757497,
      1.340419862750101, 62.45333008770111, 21.260796478808622,
      2.145416458600115, 0.5978183569453146, 2.745814563529916,
      2.326159027201714)
  def description = "LogNormal(x,x) with LogNormal(0,1) prior"
}

/**
  * Note: this are made-up goldsets. SBC on these is wildly slow.
  */
object SBCExponential extends SBCModel[Double, Continuous] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => Exponential(x))
  def goldset: List[Double] = List(0.1, 0.33, 1.12)
  def description = "Exponential(x) with LogNormal(0,1) prior"
}

object SBCLaplace extends SBCModel[Double, Continuous] {
  def sbc = SBC(LogNormal(0, 1))((x: Real) => Laplace(x, x))
  def goldset: List[Double] = List(0.1, 0.33, 1.12)
  def description = "Laplace(x,x) with LogNormal(0,1) prior"
}
