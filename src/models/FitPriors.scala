package rainier.models

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitPriors {
  val r = new scala.util.Random

  def sampleNormal(mean: Double, stddev: Double) =
    (r.nextGaussian * stddev) + mean

  def sampleUniform(from: Double, to: Double) =
    (r.nextDouble * (to - from)) + from

  def sampleExponential(lambda: Double) =
    -math.log(r.nextDouble) / lambda

  def sampleUniformNormal(mean: Double, stddev: Double, offset: Double) = {
    val m = sampleNormal(mean, stddev)
    sampleUniform(m - offset, m + offset)
  }

  def report(name: String)(fn: => Double) {
    val nSamples = 100000
    val samples = 1.to(nSamples).map { i =>
      fn
    }
    val summary = Report.Summary(samples)
    println(Report.fmtKey(name) + Report.fmtInterval(summary))
  }

  def main(args: Array[String]) {

    println("Expected results")
    report("Exponential")(sampleExponential(0.1))
    report("Normal")(sampleNormal(10, 5))
    report("Uniform")(sampleUniform(5, 15))
    report("Uniform(Normal)")(sampleUniformNormal(10, 5, 5))

    println("")
    val prior = for {
      normal <- Normal(10, 5).param
      uniform <- Uniform(5, 15).param
      exp <- Exponential(0.1).param
      unifNormal <- Uniform(normal - 5, normal + 5).param
    } yield
      Map(
        "Normal" -> normal,
        "Uniform" -> uniform,
        "Exponential" -> exp,
        "Uniform(Normal)" -> unifNormal
      )

    Report.printReport(prior, Emcee(10000, 1000, 100))
  }
}
