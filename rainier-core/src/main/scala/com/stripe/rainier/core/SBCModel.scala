package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
//import com.stripe.rainier.repl._

/**
  * Notes to self:
  * - without extends App, Scala does not recognize UniformNormal as having
  *   a main method
  * - with it, it does, but doesn't actually run it when it is selected.
  * - putting all of the SBCModels in the SBCModel object, they were also not
  *   recognized
  * - if we _do_ find a way to get Scala to recognize main methods, maybe we can
  *   define everything in rainierTests.
  */
trait SBCModel[L, T] {
  def sbc: SBC[L, T]
  def main(args: Array[String])(implicit rng: RNG): Unit =
    sbc.animate(HMC(1), 10000, 1000)
  def samples(nSamples: Int)(
      implicit rng: RNG,
      sampleable: Sampleable[(L, Real), Double]): List[Double] =
    sbc.posterior.sample(nSamples)
  def description: String
}

case object UniformNormal extends SBCModel[Continuous, Double] with App {
  def sbc = SBC(Uniform(0, 1))((x: Real) => Normal(x, 1))
  def description = "x => Normal(x,1) with Uniform(0,1) prior"
}

case object Yo {
  def main(args: Array[String]): Unit = println("yo")
}
