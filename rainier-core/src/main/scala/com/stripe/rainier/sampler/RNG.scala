package com.stripe.rainier.sampler

import scala.util.Random

trait RNG {
  def standardUniform: Double
  def standardNormal: Double
  def int(until: Int): Int =
    math.min((standardUniform * until).toInt, until - 1)
}

object RNG {
  val default: RNG = new ScalaRNG(System.currentTimeMillis)
}

case class ScalaRNG(seed: Long) extends RNG {
  val rand = new Random(seed)
  def standardUniform = rand.nextDouble
  def standardNormal = rand.nextGaussian
}
