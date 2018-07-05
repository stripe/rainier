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
            s"y ~ $description, x = $trueValue, sampler = $sampler, E(x) within 5%") {
            assert(xErr.abs < 0.05)
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
}
