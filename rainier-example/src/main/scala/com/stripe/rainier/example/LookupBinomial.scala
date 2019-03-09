package com.stripe.rainier.example

import com.stripe.rainier.core._
import com.stripe.rainier.compute._
import com.stripe.rainier.repl._
import com.stripe.rainier.sampler._

object LookupBinomial {
  def model(k: Int, observations: Seq[(Int, Double, Seq[(Int, Int)])]) = {
    val flattenedObservations = for {
      (id, _, pairs) <- observations
      (n, m) <- pairs
    } yield ((id, n), m)

    for {
      rates <- RandomVariable.fill(k)(Beta(2, 2).param)
      _ <- Predictor[(Int, Int)]
        .from {
          case (id, charges) => Binomial(Lookup(id, rates), charges)
        }
        .fit(flattenedObservations)
    } yield rates
  }

  def synthesize(k: Int, nBar: Int): Seq[(Int, Double, Seq[(Int, Int)])] = {
    val ids = 0.until(k).toList
    RandomVariable(Generator.traverse(ids.map { i =>
      for {
        rate <- Beta(2, 2).generator
        tuples <- (for {
          n <- Poisson(nBar).generator
          m <- Binomial(rate, n).generator
        } yield (n, m)).repeat(10)
      } yield (i, rate, tuples)
    })).sample(1).head
  }

  def main(args: Array[String]): Unit = {
    val k = 47 //46 is much faster
    val nBar = 20
    val observations = synthesize(k, nBar)
    println("True rate: " + observations.head._2)
    plot1D(
      model(k, observations)
        .sample(HMC(5), 1000, 1000)
        .map { rates =>
          rates(0)
        })
  }
}
