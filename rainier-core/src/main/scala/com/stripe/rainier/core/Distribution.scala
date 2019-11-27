package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  type V
  def encoder(seq: Seq[T]): Encoder.Aux[T,V]
  def logDensity(v: V): Real
  def generator: Generator[T]
}

object Distribution {
  type Aux[X, Y] = Distribution[X] { type V = Y }

  def gen[D <: Distribution[T], T]: ToGenerator[D, T] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }
}
