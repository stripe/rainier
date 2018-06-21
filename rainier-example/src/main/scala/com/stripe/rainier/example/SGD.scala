package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler
import com.stripe.rainier.repl._
import scala.io.Source

object SGD {
  def model(data: List[(Int, Int)]): RandomVariable[(Real, Real)] =
    for {
      a <- LogNormal(0, 1).param
      b <- LogNormal(0, 1).param
    //  _ <- data.fit(Predictor.from { k: Real =>
        BetaBinomial(a, b, k)
      })
    } yield (a, b)

  def main(args: Array[String]): Unit = {
    val data: List[(Int, Int)] =
      Source
        .fromFile(args(0))
        .getLines
        .map { line =>
          val parts = line.split("\t")
          (parts(0).toInt, parts(1).toInt)
        }
        .toList

    val m = model(data)

    val (a, b) = m.optimize(sampler.SGD(0.001), 5)
    println(s"Beta($a,$b)")
  }
}
