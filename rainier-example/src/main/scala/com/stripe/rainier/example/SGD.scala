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
      _ <- fit(data, 100) { (k, n) =>
        BetaBinomial.logDensity(a, b, k, n)
      }
    } yield (a, b)

  //super hacky and manual
  def fit(data: List[(Int, Int)], batchSize: Int)(
      fn: (Real, Real) => Real): RandomVariable[Unit] = {
    val batchVariables = 1.to(batchSize).map { _ =>
      (new Variable, new Variable)
    }
    val density = Real.sum(batchVariables.map(fn.tupled))
    val buffers = 1.to(batchSize).map { _ =>
      (new scala.collection.mutable.ArrayBuffer[Double],
       new scala.collection.mutable.ArrayBuffer[Double])
    }
    data.grouped(batchSize).foreach { batch =>
      if (batch.size == batchSize) {
        batch.zip(buffers).foreach {
          case ((k, n), (kBuf, nBuf)) =>
            kBuf += k.toDouble
            nBuf += n.toDouble
        }
      }
    }
    val columns =
      batchVariables.zip(buffers).flatMap {
        case ((kVar, nVar), (kBuf, nBuf)) =>
          List((kVar, kBuf.toArray), (nVar, nBuf.toArray))
      }
    RandomVariable((), density, columns.toMap)
  }

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
