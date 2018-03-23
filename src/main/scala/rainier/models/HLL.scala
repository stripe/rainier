package rainier.models

import scala.util.hashing.MurmurHash3
import rainier.compute.Real

case class HLL(bits: Int) {
  val size = Math.pow(2, bits)

  //optimizing for clarity not speed
  def apply(hashes: Seq[Int]): Map[Int, Byte] =
    hashes
      .map { hash =>
        val key = hash & ((1 << bits) - 1)
        val value = trailingZeros(hash >> bits) + 1
        key -> value
      }
      .groupBy(_._1)
      .map {
        case (k, pairs) =>
          k -> pairs.map(_._2).max.toByte
      }

  def strings(strings: Seq[String]): Map[Int, Byte] =
    apply(strings.map(MurmurHash3.stringHash))

  val maxZeros = 32 - bits
  private def trailingZeros(x: Int): Int =
    if (x == 0)
      maxZeros + 1
    else if ((x & 1) == 1)
      0
    else
      1 + trailingZeros(x >>> 1)

  def logDensity(lambda: Real, hll: Map[Int, Byte]): Real = {
    val lm = lambda * Real(-1) / Real(size)
    val c = MultiplicityVector(hll)

    Real(c(0)) * lm +
      Real.sum(1.to(maxZeros).map { k =>
        Real(c(k)) * (lm / pow2(k) + (Real.one - (lm / pow2(k)).exp).log)
      }) +
      Real(c(maxZeros + 1)) * (Real.one - (lm / pow2(maxZeros + 1)).exp).log
  }

  private def pow2(n: Int) = Real(Math.pow(2, n))

  private case class MultiplicityVector(hll: Map[Int, Byte]) {
    val cs =
      hll.values
        .groupBy(_.toInt)
        .map { case (n, registers) => n -> registers.size }

    def apply(k: Int): Int =
      if (k == 0)
        size.toInt - hll.size
      else
        cs.getOrElse(k, 0)
  }

  //adapted from algebird
  def cardinality(hll: Map[Int, Byte]): Double = {
    val zeroCnt = size - hll.size
    val z = 1.0 / (zeroCnt + hll.values.map { mj =>
      math.pow(2.0, -mj)
    }.sum)
    val smallE = 5 * size / 2.0
    val factor = (0.7213 / (1.0 + 1.079 / size)) * size * size
    val e: Double = factor * z
    if (e > smallE || zeroCnt == 0) e
    else size * scala.math.log(size / zeroCnt)
  }

  def bounds(hll: Map[Int, Byte]): (Double, Double) = {
    val v = cardinality(hll)
    val stdev = 1.04 / scala.math.sqrt(size)
    val lowerBound = math.max(v * (1.0 - 3 * stdev), 0.0)
    val upperBound = v * (1.0 + 3 * stdev)
    (lowerBound, upperBound)
  }
}
