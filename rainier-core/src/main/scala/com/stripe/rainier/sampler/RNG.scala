package com.stripe.rainier.sampler

import scala.util.Random

trait RNG {
  def standardUniform: Double
  def standardNormal: Double
  def int(until: Int): Int =
    math.min((standardUniform * until).toInt, until - 1)
}

object RNG {
  lazy val default: RNG = {
    val seed = System.currentTimeMillis
    println("Initializing RNG with seed " + seed)
    ScalaRNG(seed)
  }
}

final case class ScalaRNG(seed: Long) extends RNG {
  val rand: Random = new Random(seed)
  def standardUniform: Double = rand.nextDouble
  def standardNormal: Double = rand.nextGaussian
}
