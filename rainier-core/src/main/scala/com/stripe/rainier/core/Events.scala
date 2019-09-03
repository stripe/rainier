package com.stripe.rainier.core

case class Events[X, Y, D <: Distribution[Y]](fn: X => D) {
  def apply(x: X): D = fn(x)
  def simulate(seq: Seq[X]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      fn(x).generator.map { y =>
        (x, y)
      }
    })
}

object Events {
  def observe[T, D <: Distribution[T]](seq: Seq[T],
                                       dist: D): RandomVariable[D] =
    dist.fit(seq).map { _ =>
      dist
    }

  def observe[X, Y, D <: Distribution[Y]](seq: Seq[(X, Y)])(
      fn: X => D): RandomVariable[Events[X, Y, D]] = {
    val rvs = seq.map { case (x, z) => fn(x).fit(z) }
    RandomVariable.traverse(rvs).map { _ =>
      Events(fn)
    }
  }

  def observe[X, Y, D <: Distribution[Y]](
      seq: Seq[(X, Y)],
      fn: Fn[X, D]): RandomVariable[Events[X, Y, D]] = {
    val lh = Fn.toLikelihood[X, D, Y](implicitly[ToLikelihood[D, Y]])(fn)
    lh.fit(seq).map { _ =>
      Events(fn(_))
    }
  }
}
