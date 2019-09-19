package com.stripe.rainier.core

object Events {
  def observe[T, D <: Distribution[T]](seq: Seq[T],
                                       dist: D): RandomVariable[D] =
    dist.fit(seq).map { _ =>
      dist
    }

  def observe[X, Y, D <: Distribution[Y]](xs: Seq[X], ys: Seq[Y])(
      fn: X => D): RandomVariable[X => D] =
    observe(xs.zip(ys))(fn)

  def observe[X, Y, D <: Distribution[Y]](seq: Seq[(X, Y)])(
      fn: X => D): RandomVariable[X => D] = {
    val rvs = seq.map { case (x, z) => fn(x).fit(z) }
    RandomVariable.traverse(rvs).map { _ =>
      fn
    }
  }

  def observe[X, Y, D <: Distribution[Y]](
      xs: Seq[X],
      ys: Seq[Y],
      fn: Fn[X, D]): RandomVariable[X => D] =
    observe(xs.zip(ys), fn)

  def observe[X, Y, D <: Distribution[Y]](
      seq: Seq[(X, Y)],
      fn: Fn[X, D]): RandomVariable[X => D] = {
    val lh = Fn.toLikelihood[X, D, Y](implicitly[ToLikelihood[D, Y]])(fn)
    lh.fit(seq).map { _ =>
      fn(_)
    }
  }

  def simulate[X, Y, D](seq: Seq[X])(fn: X => D)(
      implicit tg: ToGenerator[D, Y]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      tg(fn(x)).map { y =>
        (x, y)
      }
    })

  def simulate[X, Y, D](seq: Seq[X], fn: Fn[X, D])(
      implicit tg: ToGenerator[D, Y]): Generator[Seq[(X, Y)]] =
    simulate(seq)(fn(_))
}
