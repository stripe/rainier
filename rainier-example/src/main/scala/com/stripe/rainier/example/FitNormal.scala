package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object FitNormal {
  def model(k: Int) = {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(k).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)
  }

  def main(args: Array[String]) {
    plot2D(model(1000).sample())
  }
}
