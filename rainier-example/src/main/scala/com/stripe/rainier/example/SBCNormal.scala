package com.stripe.rainier.example

import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object SBCNormal {
  def main(args: Array[String]): Unit = {
    SBC[Continuous, Double](Uniform(0, 1)) { x =>
      Normal(x, 1)
    }.animate(HMC(1), 10000, 1000)
  }
}
