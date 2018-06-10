package com.stripe.rainier.sampler

import com.stripe.rainier.compute._
import scala.annotation.tailrec

trait Sampler {
  def sample(density: Real,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]]
}

final case class Diagnostics(rHat: Double, effectiveSampleSize: Double)

/**
This is a direct implementation of the equations described in
sections 30.3 (for rHat and v) and 30.4 (for autocorrelation and ess)
of the Stan manual.
**/
object Diagnostics {
  def apply(traces: Seq[Array[Double]]): Diagnostics = {
    val m = traces.size.toDouble
    val n = traces.head.size.toDouble
    val (rHat, v) = rHatAndV(traces, n, m)
    val ac = autocorrelation(traces, n, m, v, 1, 0.0)
    //note: this doesn't match the stan manual 2.17 because of a known bug
    //that will be fixed in 2.18
    val ess = n * m / (1 + (2 * ac))
    Diagnostics(rHat, ess)
  }

  def rHatAndV(traces: Seq[Array[Double]],
               n: Double,
               m: Double): (Double, Double) = {

    val means = traces.map { t =>
      t.sum / n
    }

    val meanMean = means.sum / m

    val b = (n / (m - 1)) * means.map { m =>
      Math.pow(m - meanMean, 2)
    }.sum

    val variances =
      traces.zip(means).map {
        case (t, m) =>
          t.map { a =>
            Math.pow(a - m, 2)
          }.sum / (n - 1)
      }

    val w = variances.sum / m

    val v =
      (n - 1).toDouble / n * w +
        b / n

    val rHat = Math.sqrt(v / w)
    (rHat, v)
  }

  @tailrec
  def autocorrelation(traces: Seq[Array[Double]],
                      n: Double,
                      m: Double,
                      v: Double,
                      lag: Int,
                      acc: Double): Double = {
    val vt = traces.map { trace =>
      variogram(trace, lag)
    }.sum / m
    val pt = 1.0 - (vt / (2.0 * v))
    //TODO: this termination criteria is designed for NUTS
    //and we may want something different for straight HMC
    if (pt > 0.0 && lag < 100)
      autocorrelation(traces, n, m, v, lag + 1, acc + pt)
    else
      acc
  }

  def variogram(trace: Array[Double], lag: Int): Double = {
    var i = lag
    var sum = 0.0
    while (i < trace.size) {
      sum += Math.pow(trace(i) - trace(i - lag), 2)
      i += 1
    }
    sum / (trace.size - lag).toDouble
  }
}

object Sampler {
  object Default {
    val sampler: Sampler = HMC(5)
    val iterations: Int = 10000
    val warmupIterations: Int = 100000
  }

  def diagnostics(chains: List[List[Array[Double]]]): List[Diagnostics] = {
    val nParams = chains.head.head.size
    0.until(nParams).toList.map { i =>
      val traces = chains.map { c =>
        c.map { a =>
          a(i)
        }.toArray
      }
      Diagnostics(traces)
    }
  }
}
