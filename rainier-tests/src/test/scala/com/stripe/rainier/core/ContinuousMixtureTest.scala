package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class ContinuousMixtureTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1528673302081L)

  def check(description: String)(
      fn: Real => Continuous,
      fnMean: Double => Double = (mu => mu)): Unit = {
    println(description)
    List((Walkers(100), 10000), (HMC(5), 1000)).foreach {
      case (sampler, iterations) =>
        println((sampler, iterations))
        List(0.1, 1.0, 2.0).foreach { trueValue =>
          val trueDist = fn(Real(trueValue))
          val trueMean = fnMean(trueValue)
          val syntheticData =
            RandomVariable(trueDist.generator).sample().take(1000)
          val sampledData =
            trueDist.param.sample(sampler, iterations, iterations)
          val model =
            for {
              x <- LogNormal(0, 1).param
              _ <- fn(x).fit(syntheticData)
            } yield x
          val fitValues = model.sample(sampler, iterations, iterations)

          val syntheticMean = syntheticData.sum / syntheticData.size
          val syntheticStdDev = Math.sqrt(syntheticData.map { n =>
            Math.pow(n - syntheticMean, 2)
          }.sum / syntheticData.size)
          val sampledMean = sampledData.sum / sampledData.size
          val yErr = (sampledMean - syntheticMean) / syntheticStdDev

          val fitMean = fitValues.sum / fitValues.size
          val xErr = (fitMean - trueValue) / trueMean

          test(
            s"y ~ $description, x = $trueValue, sampler = $sampler, E(y) within 0.2 SD") {
            assert(yErr.abs < 0.2)
          }

          test(
            s"y ~ $description, x = $trueValue, sampler = $sampler, x = $trueValue, E(x) = $fitMean, E(x) within 5%") {
            assert(xErr.abs < 0.05)
          }
        }
    }
  }

  def checkDensity(description: String,
                   distribution: Continuous,
                   expectedLogPDF: Double => Double) =
    List(-1.0, 0.0, 1.0).foreach { x =>
      val expectedLogDensity = expectedLogPDF(x)
      val calculatedLogDensity = distribution.logDensity(x)
      val calculatedLogDensityDouble =
        RandomVariable(Normal(calculatedLogDensity, 0.0).generator)
          .sample()
          .take(1)
          .head

      test(s"$description.logDensity, x=$x") {
        assert(((calculatedLogDensityDouble - expectedLogDensity).abs < 0.01))
      }
    }

  def normalPdf(mean: Double, sd: Double)(x: Double) =
    math.exp(-1.0 * (x - mean) * (x - mean) / (2 * sd * sd)) / math.sqrt(
      2 * math.Pi * sd * sd)
  def expPdf(lambda: Double)(x: Double) =
    if (x > 0) { lambda * math.exp(-lambda * x) } else { 0.0 }

  // This is a similar form to what we want to use for the prior (with a dependency on 'x' to make the check work)
  check("ContinuousMixture({0 -> p, Beta(0.7, 71) -> 1-p})")(
    { x =>
      ContinuousMixture(
        Map(
          Uniform(x, x + 1e-6) -> 0.318,
          Beta(0.72786618645211232, 71.210266308649764) -> (1.0 - 0.318)
        )
      )
    }, { x: Double =>
      0.318 * 0.0 + 0.72786618645211232 / (0.72786618645211232 + 71.210266308649764)
    }
  )


  checkDensity(
    "ContinuousMixture({Exponential(2) -> 0.5, Normal(0,1) -> 0.5})",
    ContinuousMixture(
      Map(
        Exponential(2) -> 0.5,
        Normal(0, 1) -> 0.5
      )
    ),
    x => math.log(0.5 * normalPdf(0, 1)(x) + 0.5 * expPdf(2)(x))
  )

  //
  check("ContinuousMixture({Exponential(x) -> 0.5, Exponential(2x) -> 0.5})")(
    { x =>
      ContinuousMixture(
        Map(
          Exponential(x) -> 0.5,
          Exponential(2 * x) -> 0.5
        )
      )
    }, { x: Double =>
      0.5 * (1.0 / x) + 0.5 * (1.0 / (2.0 * x))
    }
  )

  // This doesn't work. I'm not sure whether it's just a n_iterations thing.
  check("ContinuousMixture({Exponential(x) -> 0.5, Normal(x,1) -> 0.5})")(
    { x =>
      ContinuousMixture(
        Map(
          Exponential(x) -> 0.5,
          Normal(x, 1.0) -> 0.5
        )
      )
    }, { x: Double =>
      (0.5 * (1.0 / x) + 0.5 * x)
    }
  )

  // This 'narrowly' fails: I think we just need more iterations to improve the fit.
  check("ContinuousMixture({Normal(x,2) -> 0.5, Normal(x+1,3) -> 0.5})")(
    { x =>
      ContinuousMixture(
        Map(
          Normal(x + 1.0, 2.0) -> 0.5,
          Normal(x, 3.0) -> 0.5
        )
      )
    }, { x: Double =>
      (0.5 * x + 0.5 * (x + 1))
    }
  )

  checkDensity(
    "ContinuousMixture({Normal(1,3) -> 0.5, Normal(0,4) -> 0.5})",
    ContinuousMixture(
      Map(
        Normal(1, 3) -> 0.5,
        Normal(0, 4) -> 0.5
      )
    ),
    x => math.log(0.5 * normalPdf(1, 3)(x) + 0.5 * normalPdf(0, 4)(x))
  )
}
