package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler
import com.stripe.rainier.repl._
import scala.io.Source

object SGD {
  def model(x: Variable, y: Variable) =
    for {
      a <- LogNormal(0, 1).param
      b <- LogNormal(0, 1).param
      _ <- RandomVariable.fromDensity(
        BetaBinomial.logDensity(a, b, x, y)
      )
    } yield (a, b)

  def main(args: Array[String]): Unit = {
    val x = new Variable
    val y = new Variable
    val m = model(x, y)

    val (xData, yData) =
      Source
        .fromFile(args(0))
        .getLines
        .map { line =>
          val parts = line.split("\t")
          (parts(0).toDouble, parts(1).toDouble)
        }
        .toArray
        .unzip

    val samples =
      m.sample(sampler.SGD(0.001, Map(x -> xData, y -> yData)), 5, 1)
    val a = samples.head._1
    val b = samples.head._2
    println(s"Beta($a,$b)")
  }
}
