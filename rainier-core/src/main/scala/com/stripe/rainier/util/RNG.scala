package com.stripe.rainier.util

import scala.util.Random
import Log._

trait RNG {
  def standardUniform: Double
  def standardNormal: Double
  def int(until: Int): Int =
    math.min((standardUniform * until).toInt, until - 1)
}

object RNG {
  lazy val default: RNG = {
    val seed = System.currentTimeMillis
    ScalaRNG(seed)
  }
}

final case class ScalaRNG(seed: Long) extends RNG {
  FINE.log("Initializing RNG with seed %d", seed)

  val rand: Random = new Random(seed)
  def standardUniform: Double = rand.nextDouble
  def standardNormal: Double = rand.nextGaussian
}
