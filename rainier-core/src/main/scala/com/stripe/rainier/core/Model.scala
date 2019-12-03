package com.stripe.rainier.core

import com.stripe.rainier.compute._

case class Model(private val targets: Set[Target]) {
  def merge(other: Model) = Model(targets ++ other.targets)
}

object Model {
  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model = {
    val target = dist.target(ys)
    Model(Set(target))
  }

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val targets = (xs.zip(ys)).map {
      case (x, y) => fn(x).target(y)
    }

    Model(targets.toSet)
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    val enc = fn.encoder
    val (v, vars) = enc.create(Nil)
    val dist = fn.xy(v)
    val target = dist.target(ys)
    val cols = enc.columns(xs)
    Model(
      Set(
        new Target(target.real, target.placeholders ++ vars.zip(cols).toMap)))
  }
}
