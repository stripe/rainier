package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class DiscreteTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1527608515939L)

  def check(description: String)(fn: Real => Discrete,
                                 probs: List[Double]): Unit = {
    println(description)
    List((Walkers(100), 10000), (HMC(5), 1000)).foreach {
      case (sampler, iterations) =>
        println((sampler, iterations))
        probs.foreach { trueValue =>
          val trueDist = fn(Real(trueValue))
          val syntheticData =
            RandomVariable(trueDist.generator).sample(1000)
          val model =
            for {
              x <- Uniform(0, 1).param
              _ <- fn(x).fit(syntheticData)
            } yield x
          val fitValues = model.sample(sampler, iterations, iterations)
          val fitMean = fitValues.sum / fitValues.size
          val xErr = (fitMean - trueValue) / trueValue

          test(
            s"y ~ $description, x = $trueValue, sampler = $sampler, E(x) within 10%") {
            assert(xErr.abs < 0.1)
          }
        }
    }
  }

  def checkLogDensity(description: String)(p: Double,
                                           k: Int,
                                           t: Int,
                                           expectedDensity: Double): Unit = {
    val logDensity = Binomial(p, k).logDensity(t)
    test(s"y ~ $description, y.logDensity($t) = log($expectedDensity)") {
      assert(logDensity == Real(expectedDensity).log)
    }
  }

  /** Binomial generator, Poisson approximation, Normal approximation **/
  check("Binomial(x, 10), x = 0.1, 0.5, 1.0")(x => Binomial(x, 10),
                                              List(0.1, 0.5, 1.0))
  check("Binomial(x, 200), x = 0.01, 0.02, 0.04")(x => Binomial(x, 200),
                                                  List(0.01, 0.02, 0.04))
  check("Binomial(x, 2000), x = 0.5")(x => Binomial(x, 2000), List(0.5))

  /** Edge cases Binomial(0.0, k) and Binomial(1.0, k) **/
  checkLogDensity("Binomial(0.0, 10)")(0.0, 10, 0, 1)
  checkLogDensity("Binomial(0.0, 10)")(0.0, 10, 1, 0)
  checkLogDensity("Binomial(1.0, 10)")(1.0, 10, 10, 1)
  checkLogDensity("Binomial(1.0, 10)")(1.0, 10, 9, 0)

  /** Bernoulli test **/
  check("Bernoulli(x), x = 0.1, 0.2, 0.5, 0.8, 0.9, 1.0")(
    x => Bernoulli(x),
    List(0.1, 0.2, 0.5, 0.8, 0.9, 1.0))

  /** Geometric test **/
  check("Geometric(x), x = 0.01, 0.1, 0.5, 0.99, 1.0")(
    x => Geometric(x),
    List(0.01, 0.1, 0.5, 0.99, 1.0))

  /** Negative Binomial test **/
  check("NegativeBinomial(10, x), x = 0.1, 0.5, 0.8")(
    x => NegativeBinomial(10, x),
    List(0.1, 0.5, 0.8))

  /** Negative Binomial test, Normal Approximation **/
  check("NegativeBinomial(300, x), x = 0.2, 0.4, 0.6")(
    x => NegativeBinomial(300, x),
    List(0.2, 0.4, 0.6))

  /** Zero Inflated Geometric test **/
  check("Geometric(.3).constantInflated(0, psi), psi = 0.3, 0.5, 0.9, 1.0")(
    psi => Geometric(.3).constantInflated(0, psi),
    List(0.3, 0.5, 0.9, 1.0))

  /** Zero Inflated Poisson test, convience method check **/
  check("Poisson(5).zeroInflated(psi), psi = 0.2, 0.5, 0.9, 1.0")(
    psi => Poisson(5).constantInflated(0, psi),
    List(0.2, 0.5, 0.9, 1.0))

  /** Zero Inflated Poisson test **/
  check("Poisson(5).constantInflated(0, psi), psi = 0.2, 0.5, 0.9, 1.0")(
    psi => Poisson(5).constantInflated(0, psi),
    List(0.2, 0.5, 0.9, 1.0))

  /** Zero Inflated Negative Binomial test **/
  check(
    "NegativeBinomial(20, .3).constantInflated(0, psi), psi = 0.2, 0.5, 0.9")(
    psi => NegativeBinomial(20, .3).constantInflated(0, psi),
    List(0.2, 0.5, 0.9))

  /** Zero Inflated Negative Binomial test **/
  check("NegativeBinomial(20, p).constantInflated(0, .3), p = 0.2, 0.5, 0.9")(
    p => NegativeBinomial(20, p).constantInflated(0, .3),
    List(0.2, 0.5, 0.9))

  /** Zero Inflated Binomial test **/
  check("Binomial(.3, 20).constantInflated(0, psi), psi = 0.2, 0.5, 0.9, 1.0")(
    psi => Binomial(.3, 20).constantInflated(0, psi),
    List(0.2, 0.5, 0.9, 1.0))

  /** Zero Inflated Binomial test **/
  check("Binomial(p, 20).constantInflated(0, .3), p = 0.2, 0.5, 0.9, 1.0")(
    p => Binomial(p, 20).constantInflated(0, .3),
    List(0.2, 0.5, 0.9, 1.0))
}
