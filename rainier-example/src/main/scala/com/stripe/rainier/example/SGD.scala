package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler
import com.stripe.rainier.repl._

object SGD {
  def hmcModel(data: List[(Int, Int)]): RandomVariable[(Real, Real)] =
    for {
      u <- Uniform(0, 1).param
      v <- Exponential(0.1).param
      _ <- Beta(u * v, (1 - u) * v).binomial.fit(data)
    } yield (u, v)

  def model(data: List[(Int, Int)]): RandomVariable[(Real, Real)] =
    for {
      u <- Uniform(0, 1).param
      v <- Exponential(0.1).param
      _ <- fit(data, 100) { (k, n) =>
        BetaBinomial.logDensity(u * v, (1 - u) * v, k, n)
      }
    } yield (u, v)

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
    val trueU = 0.3
    val trueV = 8

    val data: List[(Int, Int)] =
      RandomVariable(Exponential(0.01).generator.map(_.toInt).flatMap { k =>
        BetaBinomial(trueU * trueV, (1.0 - trueU) * trueV, k).generator.map {
          n =>
            (k, n)
        }
      }).sample(100000)

    val m = model(data)

    val (u, v) = m.optimize(sampler.SGD(0.00001), 20)
    println((u, v))

    val m2 = hmcModel(data.take(1000))
    val samples = m2.sample(sampler.HMC(200), 1000, 1000)
    plot2D(samples)
  }
}
