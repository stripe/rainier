package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  def likelihoodFn: Fn[T, Real]
  def generator: Generator[T]
  override def toString = "Distribution()"
}

object Distribution {
  def gen[D <: Distribution[T], T]: ToGenerator[D, T] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }
}
