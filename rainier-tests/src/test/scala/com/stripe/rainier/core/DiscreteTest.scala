package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class DiscreteTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1527608515939L)

  def checkMeanVariance(description: String)(fn: Real => Discrete,
                                             meanFn: Double => Double,
                                             varFn: Double => Double,
                                             params: List[Double]): Unit = {
    println(description)
    List((Walkers(100), 10000), (HMC(5), 1000)).foreach {
      case (sampler, iterations) =>
        println((sampler, iterations))
        params.foreach { trueParam =>
          val trueDist = fn(Real(trueParam))
          val sampleData =
            RandomVariable(trueDist.generator).sample(10000)

          val sampleMean = sampleData.sum.toDouble / sampleData.size
          val sampleVar = sampleData
            .map { x =>
              math.pow(x - sampleMean, 2)
            }
            .sum
            .toDouble / (sampleData.size - 1)

          val trueMean = meanFn(trueParam)
          val meanErr = (sampleMean - trueMean) / trueMean

          val trueVar = varFn(trueParam)
          val varErr = (sampleVar - trueVar) / trueVar

          test(
            s"y ~ $description, x = $trueParam, sampler = $sampler, trueMean = $trueMean, sampleMean = $sampleMean, E(y) within 10%") {
            assert(trueMean == 0.0 || meanErr.abs < 0.1)
          }
          test(
            s"y ~ $description, x = $trueParam, sampler = $sampler, Var(y) within 10%") {
            assert(trueVar == 0.0 || varErr.abs < 0.1)
          }
        }
    }
  }

  /** Binomial generator, Poisson approximation, Normal approximation **/
  checkMeanVariance("Binomial(x, 10), x = 0.1, 0.5, 1.0")(
    x => Binomial(x, 10),
    p => 10 * p,
    p => 10 * p * (1.0 - p),
    List(0.1, 0.5, 1.0))
  checkMeanVariance("Binomial(x, 200), x = 0.01, 0.02, 0.04")(
    x => Binomial(x, 200),
    p => 200 * p,
    p => 200 * p * (1.0 - p),
    List(0.01, 0.02, 0.04))
  checkMeanVariance("Binomial(x, 2000), x = 0.5")(x => Binomial(x, 2000),
                                                  p => 2000 * p,
                                                  p => 2000 * p * (1.0 - p),
                                                  List(0.5))

  /** Bernoulli test **/
  checkMeanVariance("Bernoulli(x), x = 0.1, 0.2, 0.5, 0.8, 0.9, 1.0")(
    x => Bernoulli(x),
    p => p,
    p => p * (1.0 - p),
    List(0.1, 0.2, 0.5, 0.8, 0.9, 1.0))

  /** Geometric test **/
  checkMeanVariance("Geometric(x), x = 0.01, 0.1, 0.5, 0.99, 1.0")(
    x => Geometric(x),
    p => (1.0 - p) / p,
    p => (1.0 - p) / math.pow(p, 2),
    List(0.01, 0.1, 0.5, 0.99, 1.0))

  /** Negative Binomial test **/
  checkMeanVariance("NegativeBinomial(10, x), x = 0.1, 0.5, 0.8")(
    x => NegativeBinomial(10, x),
    p => 10 * p / (1.0 - p),
    p => 10 * p / math.pow(1.0 - p, 2),
    List(0.1, 0.5, 0.8))

  /** Negative Binomial test, Normal Approximation **/
  checkMeanVariance("NegativeBinomial(300, x), x = 0.2, 0.4, 0.6")(
    x => NegativeBinomial(300, x),
    p => 300 * p / (1.0 - p),
    p => 300 * p / math.pow(1.0 - p, 2),
    List(0.2, 0.4, 0.6))
}
