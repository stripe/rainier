package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

trait Placeholder[T, U] {
  def wrap(value: T): U
  def get(placeholder: U)(implicit n: Numeric[Real]): T
}

object Placeholder {
  implicit val double = new Placeholder[Double, Real] {
    def wrap(value: Double) = Real(value)
    def get(placeholder: Real)(implicit n: Numeric[Real]) =
      n.toDouble(placeholder)
  }

  implicit val int = new Placeholder[Int, Real] {
    def wrap(value: Int) = Real(value)
    def get(placeholder: Real)(implicit n: Numeric[Real]) =
      n.toInt(placeholder)
  }

  implicit def map[K, T, U](
      implicit p: Placeholder[T, U]): Placeholder[Map[K, T], Map[K, U]] =
    new Placeholder[Map[K, T], Map[K, U]] {
      def wrap(value: Map[K, T]): Map[K, U] =
        value.map { case (k, t) => k -> p.wrap(t) }
      def get(placeholder: Map[K, U])(implicit n: Numeric[Real]): Map[K, T] =
        placeholder.map { case (k, u) => k -> p.get(u) }
    }
}
