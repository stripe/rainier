package rainier.sampler

import scala.util.Random

trait RNG {
  def standardUniform: Double
  def standardNormal: Double
  def int(until: Int): Int =
    math.min(math.floor(standardUniform * until), until - 1).toInt
}

object RNG {
  val default: RNG = new ScalaRNG(System.currentTimeMillis)
}

case class ScalaRNG(seed: Long) extends RNG {
  val rand = new Random(seed)
  def standardUniform = rand.nextDouble
  def standardNormal = rand.nextGaussian
}
