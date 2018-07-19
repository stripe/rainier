package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Mapping[T, U] {
  def wrap(value: T): U
  def placeholder(): Placeholder[T, U]
}

trait Placeholder[T, U] { self =>
  def value: U
  def variables: Seq[Variable]
  def extract(value: T, acc: List[Double]): List[Double]
  def zip[A, B](other: Placeholder[A, B]): Placeholder[(T, A), (U, B)] =
    new Placeholder[(T, A), (U, B)] {
      val value = (self.value, other.value)
      val variables = self.variables ++ other.variables
      def extract(value: (T, A), acc: List[Double]) =
        self.extract(value._1, other.extract(value._2, acc))
    }
}

object Mapping {
  implicit def numeric[N](implicit num: Numeric[N]) =
    new Mapping[N, Real] {
      def wrap(value: N) = Real(value)
      def placeholder() = {
        val x = new Variable
        new Placeholder[N, Real] {
          val value = x
          val variables = List(x)
          def extract(value: N, acc: List[Double]) =
            num.toDouble(value) :: acc
        }
      }
    }

  implicit def zip[A, B, X, Y](implicit ab: Mapping[A, B],
                               xy: Mapping[X, Y]): Mapping[(A, X), (B, Y)] =
    new Mapping[(A, X), (B, Y)] {
      def wrap(value: (A, X)): (B, Y) =
        (ab.wrap(value._1), xy.wrap(value._2))
      def placeholder() =
        ab.placeholder.zip(xy.placeholder)
    }

  def map[K, T, U](keys: Seq[K])(
      implicit tu: Mapping[T, U]): Mapping[Map[K, T], Map[K, U]] =
    new Mapping[Map[K, T], Map[K, U]] {
      def wrap(value: Map[K, T]): Map[K, U] =
        value.map { case (k, t) => k -> tu.wrap(t) }

      def placeholder() =
        new Placeholder[Map[K, T], Map[K, U]] {
          val tuPlaceholders = keys.map { k =>
            k -> tu.placeholder()
          }
          val value = tuPlaceholders.map { case (k, p) => k -> p.value }.toMap
          val variables = tuPlaceholders.map(_._2.variables).reduce(_ ++ _)
          def extract(value: Map[K, T], acc: List[Double]) =
            ???
        }
    }

  def enum[T](keys: Seq[T]): Mapping[T, Map[T, Real]] =
    new Mapping[T, Map[T, Real]] {
      def wrap(value: T): Map[T, Real] = Map(value -> Real.one)
      def placeholder() = new Placeholder[T, Map[T, Real]] {
        val variables = keys.map { _ =>
          new Variable
        }
        val value = keys.zip(variables).toMap
        def extract(value: T, acc: List[Double]) =
          keys.toList.map { t =>
            if (t == value) 1.0 else 0.0
          } ++ acc
      }
    }
}
