package com.stripe.rainier.core

import com.stripe.rainier.compute.Real
import com.stripe.rainier.unused

trait Placeholder[T, U] {
  def wrap(value: T): U
  def get(placeholder: U)(implicit n: Numeric[Real]): T
  def requirements(placeholder: U): Set[Real]
}

object Placeholder {
  implicit val double = new Placeholder[Double, Real] {
    def wrap(value: Double) = Real(value)
    def get(placeholder: Real)(implicit n: Numeric[Real]) =
      n.toDouble(placeholder)
    def requirements(placeholder: Real) = Set(placeholder)
  }

  implicit val int = new Placeholder[Int, Real] {
    def wrap(value: Int) = Real(value)
    def get(placeholder: Real)(implicit n: Numeric[Real]) =
      n.toInt(placeholder)
    def requirements(placeholder: Real) = Set(placeholder)
  }

  implicit def map[K, T, U](
      implicit p: Placeholder[T, U]): Placeholder[Map[K, T], Map[K, U]] =
    new Placeholder[Map[K, T], Map[K, U]] {
      def wrap(value: Map[K, T]): Map[K, U] =
        value.map { case (k, t) => k -> p.wrap(t) }
      def get(placeholder: Map[K, U])(implicit n: Numeric[Real]): Map[K, T] =
        placeholder.map { case (k, u) => k -> p.get(u) }
      def requirements(placeholder: Map[K, U]) =
        placeholder.values.flatMap { u =>
          p.requirements(u)
        }.toSet
    }

  implicit def zip[A, B, X, Y](
      implicit ab: Placeholder[A, B],
      xy: Placeholder[X, Y]): Placeholder[(A, X), (B, Y)] =
    new Placeholder[(A, X), (B, Y)] {
      def wrap(value: (A, X)): (B, Y) =
        (ab.wrap(value._1), xy.wrap(value._2))
      def get(placeholder: (B, Y))(implicit n: Numeric[Real]): (A, X) =
        (ab.get(placeholder._1), xy.get(placeholder._2))
      def requirements(placeholder: (B, Y)): Set[Real] =
        ab.requirements(placeholder._1) ++ xy.requirements(placeholder._2)
    }

  def enum[T](@unused keys: Iterable[T]): Placeholder[T, Map[T, Real]] =
    new Placeholder[T, Map[T, Real]] {
      def wrap(value: T): Map[T, Real] = Map(value -> Real.one)
      def get(placeholder: Map[T, Real])(implicit n: Numeric[Real]): T =
        placeholder.find { case (_, r) => n.toDouble(r) > 0 }.get._1
      def requirements(placeholder: Map[T, Real]) = placeholder.values.toSet
    }
}
