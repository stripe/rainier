package com.stripe.rainier.compute

/**
  * Describes a mapping from an arbitrary class with known key/value pairs, such as a case class,
  * to a Map[String, Double].
  *
  * This allows for T to be encoded in real-space via lifting the map to a Map[String, Real].
  * @tparam T
  */
trait ToMap[T] {
  def fields: Seq[String]

  def apply(t: T): Map[String, Double]
}
