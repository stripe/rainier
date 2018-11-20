package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Placeholder[T, U] { self =>
  def value: U
  def variables(acc: List[Variable]): List[Variable]
  def extract(t: T, acc: List[Double]): List[Double]
  def zip[A, B](other: Placeholder[A, B]): Placeholder[(T, A), (U, B)] =
    new Placeholder[(T, A), (U, B)] {
      val value = (self.value, other.value)
      def variables(acc: List[Variable]) =
        self.variables(other.variables(acc))
      def extract(value: (T, A), acc: List[Double]) =
        self.extract(value._1, other.extract(value._2, acc))
    }
}
