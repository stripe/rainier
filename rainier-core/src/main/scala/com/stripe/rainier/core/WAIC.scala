package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.ir._

case class WAIC(lppd: Double, pWAIC: Double) {
  val value = (lppd - pWAIC) * -2
  def +(other: WAIC) = WAIC(lppd + other.lppd, pWAIC + other.pWAIC)
}

object WAIC {
  def apply(samples: List[Array[Double]],
            variables: List[Variable],
            target: Target): WAIC =
    if (target.nRows == 0)
      WAIC(0.0, 0.0)
    else {
      val cf = Compiler.default.compile(
        target.placeholderVariables ++ variables,
        List(("logLikelihood", target.real)))
      val globalBuf = new Array[Double](cf.numGlobals)
      val inputBuf = new Array[Double](cf.numInputs)
      val data = target.placeholderVariables.map { v =>
        target.placeholders(v)
      }.toArray
      val sampleArray = samples.toArray

      val logLikelihoods = new Array[Double](samples.size)
      //allocation free from here on
      var i = 0
      val nRows = target.nRows

      var lppd = 0.0
      var pWAIC = 0.0
      while (i < nRows) {
        updateLikelihoods(sampleArray,
                          cf,
                          inputBuf,
                          globalBuf,
                          logLikelihoods,
                          data,
                          i)
        val totalLoglikelihood = logSumExp(logLikelihoods)
        val logMeanLikelihood = totalLoglikelihood - Math.log(
          samples.size.toDouble)
        lppd += logMeanLikelihood
        pWAIC += variance(logLikelihoods)
        i += 1
      }

      WAIC(lppd, pWAIC)
    }

  private def updateLikelihoods(samples: Array[Array[Double]],
                                cf: CompiledFunction,
                                inputBuf: Array[Double],
                                globalsBuf: Array[Double],
                                logLikelihoods: Array[Double],
                                data: Array[Array[Double]],
                                i: Int): Unit = {
    val nInputs = inputBuf.size
    val nDataInputs = data.size
    val nSampleInputs = nInputs - nDataInputs
    val nSamples = samples.size
    var j = 0
    while (j < nDataInputs) {
      inputBuf(j) = data(j)(i)
      j += 1
    }
    var k = 0
    while (k < nSamples) {
      val sample = samples(k)
      j = 0
      while (j < nSampleInputs) {
        inputBuf(nDataInputs + j) = sample(j)
        j += 1
      }
      logLikelihoods(k) = cf.output(inputBuf, globalsBuf, 0)
      k += 1
    }
  }

  private def logSumExp(values: Array[Double]): Double = {
    val max = values.max
    var expSum = 0.0
    var i = 0
    val n = values.size
    while (i < n) {
      expSum += Math.exp(values(i) - max)
      i += 1
    }
    Math.log(expSum) + max
  }

//welford's online variance algorithm, from wikipedia
  private def variance(values: Array[Double]): Double = {
    var count = 0
    var mean = 0.0
    var m2 = 0.0
    val n = values.size
    while (count < n) {
      val newValue = values(count)
      count += 1
      val delta = newValue - mean
      mean += (delta / count.toDouble)
      val delta2 = newValue - mean
      m2 += (delta * delta2)
    }

    m2 / count.toDouble
  }
}
