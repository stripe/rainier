package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Distribution[T] {
  type V
  private[core] def encoder: Encoder.Aux[T, V]
  private[core] def logDensity(v: V): Real

  private[core] def target(ts: Seq[T]): Target = {
    val enc = encoder
    val (v, vars) = enc.create(Nil)
    val lh = logDensity(v)
    val cols = enc.columns(ts)
    new Target(lh, vars.zip(cols).toMap)
  }

  private[core] def target(t: T): Target = {
    val enc = encoder
    val v = enc.wrap(t)
    val lh = logDensity(v)
    Target(lh)
  }

  def generator: Generator[T]
}

object Distribution {
  type Aux[X, Y] = Distribution[X] { type V = Y }

  def gen[D <: Distribution[T], T]: ToGenerator[D, T] =
    new ToGenerator[D, T] {
      def apply(d: D) = d.generator
    }
}
