package com.stripe.rainier.example

import com.stripe.rainier.compute.Real
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object Funnel {
  val model: RandomVariable[(Real, Real)] =
    for {
      y <- Normal(0, 3).param
      x <- RandomVariable.traverse(1.to(9).map { _ =>
        Normal(0, (y / 2).exp).param
      })
    } yield (x(0), y)

  def main(args: Array[String]): Unit = {
    plot2D(model.sample(HMC(5), 1000, 10000))
  }
}
