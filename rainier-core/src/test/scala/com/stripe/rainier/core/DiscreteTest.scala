package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class DiscreteTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1527191140203L)

  def check[N: Numeric](description: String)(
      fn: Real => Distribution[N]): Unit = {
    println(description)
    List((Walkers(100), 10000), (HMC(5), 1000)).foreach {
      case (sampler, iterations) =>
        println((sampler, iterations))
        List(0.1, 0.5, 1.0).foreach { trueValue =>
          val trueDist = fn(Real(trueValue))
          val syntheticData =
            RandomVariable(trueDist.generator).sample().take(1000)
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

  check("Binomial(x,10)") { x =>
    Binomial(x, 10)
  }
}
