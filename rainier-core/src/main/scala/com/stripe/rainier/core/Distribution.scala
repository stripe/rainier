package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  type V
  def logDensity(v: V): Real
  
  def logDensity(t: T): Real = 
    logDensity(encoder.wrap(t))
  def logDensity(ts: Seq[T]): Real =
    logDensity(encoder.encode(ts))
  
  private[core] def encoder: Encoder.Aux[T, V]

  def generator: Generator[T]
}

object Distribution {
  type Aux[X, Y] = Distribution[X] { type V = Y }

  def gen[D <: Distribution[T], T]: ToGenerator[D, T] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }
}
