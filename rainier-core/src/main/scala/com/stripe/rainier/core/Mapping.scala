package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Wrapping[T,U] {
  def wrap(t: T): U
}

trait Mapping[T, U] extends Wrapping[T,U] {
  def get(u: U)(implicit n: Numeric[Real]): T
  def requirements(u: U): Set[Real]
}

trait LowPriMappings {
  implicit val int =
    new Mapping[Int, Real] {
      def wrap(t: Int) = Real(t)
      def get(u: Real)(implicit n: Numeric[Real]) =
        n.toDouble(u).toInt
      def requirements(u: Real): Set[Real] = Set(u)
    }
}

object Mapping extends LowPriMappings {
  implicit val double =
    new Mapping[Double, Real] {
      def wrap(t: Double) = Real(t)
      def get(u: Real)(implicit n: Numeric[Real]) =
        n.toDouble(u)
      def requirements(u: Real): Set[Real] = Set(u)
    }

  implicit def zip[A, B, X, Y](implicit ab: Mapping[A, B],
                               xy: Mapping[X, Y]): Mapping[(A, X), (B, Y)] =
    new Mapping[(A, X), (B, Y)] {
      def wrap(t: (A, X)) =
        (ab.wrap(t._1), xy.wrap(t._2))
      def get(u: (B, Y))(implicit n: Numeric[Real]) =
        (ab.get(u._1), xy.get(u._2))
      def requirements(u: (B, Y)) =
        ab.requirements(u._1) ++ xy.requirements(u._2)
    }

  def map[K, T, U](implicit tu: Mapping[T, U]): Mapping[Map[K, T], Map[K, U]] =
    new Mapping[Map[K, T], Map[K, U]] {
      def wrap(t: Map[K, T]): Map[K, U] =
        t.map { case (k, v) => k -> tu.wrap(v) }
      def get(u: Map[K, U])(implicit n: Numeric[Real]): Map[K, T] =
        u.map { case (k, x) => k -> tu.get(x) }.toMap
      def requirements(u: Map[K, U]): Set[Real] =
        u.values.flatMap { x =>
          tu.requirements(x)
        }.toSet
    }

  def item[T]: Mapping[T, Map[T, Real]] =
    new Mapping[T, Map[T, Real]] {
      def wrap(t: T): Map[T, Real] = Map(t -> Real.one)
      def get(u: Map[T, Real])(implicit n: Numeric[Real]): T =
        u.find { case (_, r) => n.toDouble(r) > 0 }.get._1
      def requirements(u: Map[T, Real]): Set[Real] =
        u.values.toSet
    }
}
