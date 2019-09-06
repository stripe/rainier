package com.stripe.rainier.core

object Events {
  def observe[T, D <: Distribution[T]](seq: Seq[T],
                                       dist: D): RandomVariable[D] =
    dist.fit(seq).map { _ =>
      dist
    }

  def observe[X, Y, D <: Distribution[Y]](seq: Seq[(X, Y)])(
      fn: X => D): RandomVariable[X => D] = {
    val rvs = seq.map { case (x, z) => fn(x).fit(z) }
    RandomVariable.traverse(rvs).map { _ =>
      fn
    }
  }

  def observe[X, Y, D <: Distribution[Y]](
      seq: Seq[(X, Y)],
      fn: Fn[X, D]): RandomVariable[Fn[X, D]] = {
    val lh = Fn.toLikelihood[X, D, Y](implicitly[ToLikelihood[D, Y]])(fn)
    lh.fit(seq).map { _ =>
      fn
    }
  }
}
