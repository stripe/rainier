package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Placeholder[T, U] {
  def create(): U
  def variables(u: U, acc: List[Variable]): List[Variable]
  def extract(t: T, acc: List[Double]): List[Double]
}

trait LowPriPlaceholders {
  implicit val int: Placeholder[Int, Variable] =
    new Placeholder[Int, Variable] {
      def create() = new Variable
      def variables(u: Variable, acc: List[Variable]) =
        u :: acc
      def extract(t: Int, acc: List[Double]) =
        t.toDouble :: acc
    }
}

object Placeholder extends LowPriPlaceholders {
  implicit val double: Placeholder[Double, Variable] =
    new Placeholder[Double, Variable] {
      def create() = new Variable
      def variables(u: Variable, acc: List[Variable]) =
        u :: acc
      def extract(t: Double, acc: List[Double]) =
        t :: acc
    }

  implicit def zip[A, B, X, Y](
      implicit a: Placeholder[A, X],
      b: Placeholder[B, Y]): Placeholder[(A, B), (X, Y)] =
    new Placeholder[(A, B), (X, Y)] {
      def create() = (a.create(), b.create())
      def variables(u: (X, Y), acc: List[Variable]) =
        a.variables(u._1, b.variables(u._2, acc))
      def extract(t: (A, B), acc: List[Double]) =
        a.extract(t._1, b.extract(t._2, acc))
    }
}
