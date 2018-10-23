package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.repl._

object LookupNormal {
  def model(k: Int, n: Int): RandomVariable[(Real, Real)] = {
    for {
      globalMean <- Uniform(0, 10).param
      means <- RandomVariable
        .fill(k)(Normal(globalMean, 10).param)
        .map(Lookup(_))
      stddev <- Uniform(0, 10).param
      _ <- Predictor
        .fromInt { i =>
          Normal(means(i), stddev)
        }
        .fit(synthesize(k, n))
    } yield (globalMean, stddev)
  }

  def synthesize(k: Int, n: Int): Seq[(Int, Double)] = {
    val r = new scala.util.Random
    val trueStddev = 2.0
    val globalMean = 3.0
    val means = List.fill(k)((r.nextGaussian * 10) + globalMean)
    List.fill(n) {
      val i = r.nextInt(k)
      val v = (r.nextGaussian * trueStddev) + means(i)
      (i, v)
    }
  }

  def main(args: Array[String]): Unit = {
    plot2D(model(100, 10000).sample())
  }
}
