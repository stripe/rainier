package com.stripe.rainier.core

sealed trait Events[D, T] {
  def observe(seq: Seq[D]): RandomVariable[T]
  def map[U](fn: T => U): Events[D, U] = MapEvents(this, fn)
}

object Events {
  def from[X, Y, T](fn: X => T)(
      implicit ev: T <:< Distribution[Y]): Events[(X, Y), X => T] =
    FunctionEvents(fn, ev)
}

private case class MapEvents[D, T, U](orig: Events[D, T], fn: T => U)
    extends Events[D, U] {
  def observe(seq: Seq[D]) = orig.observe(seq).map(fn)
}

private case class LikelihoodEvents[D, L](value: L, lh: ToLikelihood[L, D])
    extends Events[D, L] {
  def observe(seq: Seq[D]) = lh(value).fit(seq).map { _ =>
    value
  }
}

private case class FunctionEvents[X, Y, T](fn: X => T,
                                           ev: T <:< Distribution[Y])
    extends Events[(X, Y), X => T] {
  def observe(seq: Seq[(X, Y)]) = {
    val rvs = seq.map {
      case (x, y) =>
        ev(fn(x)).fit(y)
    }
    RandomVariable.traverse(rvs).map { _ =>
      fn
    }
  }
}
