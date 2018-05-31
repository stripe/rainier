package com.stripe.rainier.example

import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object SBCNormal {
  def main(args: Array[String]): Unit = {
    val _ =
      SBC(Uniform(0, 1)) { x =>
        Normal(x, 1)
      }.prepare(HMC(5), 1000, 1000)
        .animate(3)
  }
}
