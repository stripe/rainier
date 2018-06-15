package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class DiscreteTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1527608515939L)

  def check[N: Numeric](description: String)(fn: Real => Distribution[N],
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

  /** Binomial generator, Poisson approximation, Normal approximation **/
  check("Binomial(p, 10), p = 0.1, 0.5, 1.0")(p => Binomial(p, 10),
                                              List(0.1, 0.5, 1.0))
  check("Binomial(p, 200), p = 0.01, 0.02, 0.04")(p => Binomial(p, 200),
                                                  List(0.01, 0.02, 0.04))
  check("Binomial(p, 200), p = 0.5")(p => Binomial(p, 2000), List(0.5))
}
