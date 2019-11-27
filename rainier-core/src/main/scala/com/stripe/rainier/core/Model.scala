package com.stripe.rainier.core

import com.stripe.rainier.compute._

case class Model(targets: List[Target]) {
    def ++(other: Model) = Model(targets ++ other.targets)
}

object Model {
    def observe[X,Y](ys: Seq[Y], dist: Distribution[Y]): Model =  {
        ???
    }

    def observe[X,Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model =  {
        ???
    }

    def observe[X,Y](xs: Seq[X], ys: Seq[Y], fn: Fn[X,Distribution[Y]]): Model = {
        ???
    }
}