package com.stripe.rainier.core

sealed trait Data[D, T] {
  def observe(seq: Seq[D]): RandomVariable[T]
  def map[U](fn: T => U): Data[D, U] = MapData(this, fn)
}

object Data {
  def fit[D, L](value: L)(implicit lh: ToLikelihood[L, D]): Data[D, L] =
    LikelihoodData(value, lh)

  def fit[X, Y, L](fn: X => L)(
      implicit lh: ToLikelihood[L, Y]): Data[(X, Y), X => L] =
    FunctionData(fn, lh)
}

private case class MapData[D, T, U](orig: Data[D, T], fn: T => U)
    extends Data[D, U] {
  def observe(seq: Seq[D]) = orig.observe(seq).map(fn)
}

private case class LikelihoodData[D, L](value: L, lh: ToLikelihood[L, D])
    extends Data[D, L] {
  def observe(seq: Seq[D]) = lh(value).fit(seq).map { _ =>
    value
  }
}

private case class FunctionData[X, Y, L](fn: X => L, lh: ToLikelihood[L, Y])
    extends Data[(X, Y), X => L] {
  def observe(seq: Seq[(X, Y)]) = {
    val rvs = seq.map { case (x, y) => lh(fn(x)).fit(y) }
    RandomVariable.traverse(rvs).map { _ =>
      fn
    }
  }
}
