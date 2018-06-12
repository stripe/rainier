package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._
import scala.io.Source

object Mike {
  def model(data: List[(Int, Int)]) =
    for {
      a <- LogNormal(0, 1).param
      b <- LogNormal(0, 1).param
      _ <- Beta(a, b).binomial.fit(data)
    } yield (a, b)

  def main(args: Array[String]): Unit = {
    val data =
      Source
        .fromFile(args(0))
        .getLines
        .map { line =>
          val parts = line.split("\t")
          val chargeCount = parts(0).toInt
          val rate = parts(1).toDouble
          val disputeCount = (chargeCount * rate).toInt
          (chargeCount, disputeCount)
        }
        .toList

    val m = model(data.toList)
    plot2D(m.sample(HMC(5), 1000, 1000))
  }
}
