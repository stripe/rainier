package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Placeholder[T, U] {
  def wrap(value: T): U
  def create(): (U, Seq[Variable])
  def extract(value: T, acc: List[Double]): List[Double]
}

trait LowPriPlaceholders {
  implicit val int = new Placeholder[Int, Real] {
    def wrap(value: Int) = Real(value)
    def create() = {
      val x = new Variable
      (x, List(x))
    }
    def extract(value: Int, acc: List[Double]) =
      value.toDouble :: acc
  }
}

object Placeholder extends LowPriPlaceholders {
  implicit val double = new Placeholder[Double, Real] {
    def wrap(value: Double) = Real(value)
    def create() = {
      val x = new Variable
      (x, List(x))
    }
    def extract(value: Double, acc: List[Double]) =
      value :: acc
  }

  implicit def zip[A, B, X, Y](
      implicit ab: Placeholder[A, B],
      xy: Placeholder[X, Y]): Placeholder[(A, X), (B, Y)] =
    new Placeholder[(A, X), (B, Y)] {
      def wrap(value: (A, X)): (B, Y) =
        (ab.wrap(value._1), xy.wrap(value._2))
      def create() = {
        val (b, bv) = ab.create()
        val (y, yv) = xy.create()
        ((b, y), bv ++ yv)
      }
      def extract(value: (A, X), acc: List[Double]) =
        ab.extract(value._1, xy.extract(value._2, acc))
    }
}
/*
trait LowLowPriPlaceholders {
  implicit def item[T]: Placeholder[T, Map[T, Real]] =
    new Placeholder[T, Map[T, Real]] {
      def wrap(value: T): Map[T, Real] = Map(value -> Real.one)
      def get(placeholder: Map[T, Real])(implicit n: Numeric[Real]): T =
        placeholder.find { case (_, r) => n.toDouble(r) > 0 }.get._1
      def requirements(placeholder: Map[T, Real]) = placeholder.values.toSet
      def create() = {
        val x = new Variable
        (x, List(x))
      }
      def mapping(value: Int, placeholder: Real) =
        Map(placeholder -> value.toDouble)
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
      def create() = {
        val (b, bv) = ab.create()
        val (y, yv) = xy.create()
        ((b, y), bv ++ yv)
      }
      def mapping(value: (A, X), placeholder: (B, Y)) =
        ab.mapping(value._1, placeholder._1) ++
          xy.mapping(value._2, placeholder._2)
    }
}
 */
