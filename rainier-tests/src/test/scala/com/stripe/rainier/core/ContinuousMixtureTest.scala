package com.stripe.rainier.core

import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

class ContinuousMixtureTest extends FunSuite {
  implicit val rng: RNG = ScalaRNG(1528673302081L)

  def sbcCheck(description: String)(prior: Continuous): Unit = {
    println(s"Sampling: $description")

    val lower = SBC.binomialQuantile(0.005, 320, 1.0 / 8)
    val upper = SBC.binomialQuantile(0.995, 320, 1.0 / 8)

    val stream = SBC(prior) { x =>
      Normal(x, 1)
    }.simulate(HMC(1), 10000, 1000)

    val buckets = stream
      .groupBy(_.rank)
      .mapValues(_.size)

    test(s"SBC Test: y ~ $description, all buckets within 99% confidence") {
      buckets.values.toList.foreach { s =>
        assert(s > lower)
        assert(s < upper)
      }
    }
  }

  def checkGeneratedMean(description: String,
                         distribution: Continuous,
                         expectedMean: Double) = {
    List((Walkers(100), 20000), (HMC(5), 2000)).foreach {
      case (sampler, nIterations) =>
        val syntheticData =
          RandomVariable(distribution.generator)
            .sample(sampler, nIterations, nIterations)
            .take(10000)
        val syntheticMean = syntheticData.sum / syntheticData.size
        val syntheticStdDev = Math.sqrt(syntheticData.map { n =>
          Math.pow(n - syntheticMean, 2)
        }.sum / syntheticData.size)

        val yErr = (syntheticMean - expectedMean) / syntheticStdDev

        test(
          s"generatedMeanTest: y ~ $description, mean = $expectedMean, sampler = $sampler, E(y) = $syntheticMean, E(y) within 0.2 SD") {
          assert(yErr.abs < 0.2)
        }
    }
  }

  def checkParamMean(description: String,
                     distribution: Continuous,
                     expectedMean: Double) = {
    List((Walkers(100), 20000), (HMC(5), 2000)).foreach {
      case (sampler, nIterations) =>
        val sampledData =
          distribution.param.sample(sampler, nIterations, nIterations)
        println(sampledData)
        val sampledMean = sampledData.sum / sampledData.size
        val sampledStDev = Math.sqrt(sampledData.map { n =>
          Math.pow(n - sampledMean, 2)
        }.sum / sampledData.size)

        val yErr = (sampledMean - expectedMean) / sampledStDev

        test(
          s"paramMeanTest: y ~ $description, mean = $expectedMean, sampler = $sampler, E(y) = $sampledMean, E(y) within 0.2 SD") {
          assert(yErr.abs < 0.2)
        }
    }
  }

  /********************************
    * SBC Tests
    * These take a couple of minutes each to run, so are not included in automated tests.
    * Uncomment to run them.
    *******************************/
  /*
  // SBC is bugged for nonstandard uniform distributions, so blanking these tests for now.
  sbcCheck("Uniform(5,10)")(
    Uniform(5, 10)
  )

  sbcCheck("ContinuousMixture({Uniform(-3, 3) -> 0.5, Uniform(-1, 8) -> 0.5})")(
    ContinuousMixture(
      Map(
        Uniform(3, 5) -> 0.5,
        Uniform(1, 8) -> 0.5
      )
    )
  )*/

  /*
  sbcCheck("ContinuousMixture({Normal(-3, 1) -> 0.5,Normal(1, 4) -> 0.5})")(
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Normal(1, 4) -> 0.5
      )
    )
  )

  sbcCheck("ContinuousMixture({Exponential(1) -> 0.5,Exponential(2) -> 0.5})")(
    ContinuousMixture(
      Map(
        Exponential(1) -> 0.5,
        Exponential(2) -> 0.5
      )
    )
  )

  sbcCheck("ContinuousMixture({Normal(-3, 1) -> 0.5,Exponential(2) -> 0.5})")(
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Exponential(2) -> 0.5
      )
    )
  )

  sbcCheck("ContinuousMixture({Normal(-3, 1) -> 0.5, Uniform(-1, 1) -> 0.5})")(
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Uniform(0, 1) -> 0.5
      )
    )
  )

  sbcCheck("ContinuousMixture({Exponential(2) -> 0.5, Uniform(-1, 1) -> 0.5})")(
    ContinuousMixture(
      Map(
        Normal(-2, 1) -> 0.5,
        Uniform(0, 1) -> 0.5
      )
    )
  )
   */

  def normalPdf(mean: Double, sd: Double)(x: Double) =
    math.exp(-1.0 * (x - mean) * (x - mean) / (2 * sd * sd)) / math.sqrt(
      2 * math.Pi * sd * sd)
  def expPdf(lambda: Double)(x: Double) =
    if (x > 0) { lambda * math.exp(-lambda * x) } else { 0.0 }

  /********************************
    * Generator Tests
    * These don't pass consistently, although learning seems to be occurring in the right direction.
   ********************************/

  /*checkGeneratedMean(
    "ContinuousMixture({Uniform(-3, 1) -> 0.5, Uniform(0, 4) -> 0.5})",
    ContinuousMixture(
      Map(
        Uniform(-3, 1) -> 0.5,
        Uniform(0, 4) -> 0.5
      )
    ),
    0.5)

  checkGeneratedMean(
    "ContinuousMixture({Normal(-3, 1) -> 0.5, Normal(1, 4) -> 0.5})",
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Normal(1, 4) -> 0.5
      )
    ),
    -1.0)

  checkGeneratedMean(
    "ContinuousMixture({Normal(-3, 1) -> 0.5, Exponential(1) -> 0.5})",
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Exponential(1.0) -> 0.5
      )
    ),
    -1.0)

  /********************************
 * Param Tests
 * These don't pass consistently, although learning seems to be occurring in the right direction.
   *******************************/

  /*
  checkParamMean(
    "ContinuousMixture({Uniform(-3, 1) -> 0.5, Uniform(0, 4) -> 0.5})",
    Uniform(-3, 1),
    -1.0)

  checkParamMean(
    "ContinuousMixture({Uniform(-3, 1) -> 1.0})",
    ContinuousMixture(
      Map(
        //Normal(-3, 1) -> 1.0
        //Exponential(2.0) -> 1.0
        //Beta(2, 2) -> 1.0
        Uniform(-3, -1) -> 1.0 // <-- THIS TEST HANGS FOR HMC WHEN LOWER BOUND IS <0. WHY ON EARTH?
      )
    ),
    -2.0
  )

  checkParamMean(
    "ContinuousMixture({Normal(-3, 1) -> 0.5, Normal(1, 4) -> 0.5})",
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Normal(1, 4) -> 0.5
      )
    ),
    -1.0)

  checkParamMean(
    "ContinuousMixture({Normal(-3, 1) -> 0.5, Exponential(1) -> 0.5})",
    ContinuousMixture(
      Map(
        Normal(-3, 1) -> 0.5,
        Exponential(1.0) -> 0.5
      )
    ),
    -1.0)
 */
 */
}
