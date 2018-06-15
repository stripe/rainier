package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler
import com.stripe.rainier.repl._
import scala.io.Source

object SGD {
  def model(data: Data[(Int, Int)]) =
    for {
      a <- LogNormal(0, 1).param
      b <- LogNormal(0, 1).param
      _ <- BetaBinomial(a, b).fit(data)
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

    val m = model(Data(data))

    val (a, b) = m.optimize(sampler.SGD(0.001), 5)
    println(s"Beta($a,$b)")
  }
}
