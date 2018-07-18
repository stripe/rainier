/*package com.stripe.rainier.core

import com.stripe.rainier.compute._
import scala.collection.mutable.ArrayBuffer

sealed trait Fittable[L, T] {
  def single(pdf: L, value: T): Target
  def sequence(pdf: L, value: Seq[T]): Target
}

object Fittable {
  implicit def placeholder[L, T, U](
      implicit lh: PlaceholderLikelihood[L, T, U]
  ): Fittable[L, T] =
    PlaceholderFittable(lh)
}

private final case class SimpleFittable[L, T] private (lh: Likelihood[L, T])
    extends Fittable[L, T] {
  def single(pdf: L, value: T) =
    Target(lh.logDensity(pdf, value))
  def sequence(pdf: L, seq: Seq[T]) =
    Target(Real.sum(seq.map { t =>
      lh.logDensity(pdf, t)
    }))
}

private final case class PlaceholderFittable[L, T, U] private (
    lh: PlaceholderLikelihood[L, T, U]
) extends Fittable[L, T] {

}
 */
