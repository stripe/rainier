package com.stripe.rainier.core

import com.stripe.rainier.sampler._

object VarianceExample {
  class Config1 extends SamplerConfig {
    val warmupIterations = 1000
    val iterations = 1000
    val statsWindow = 100
    def sampler() = new EHMCSampler(1, 50, 100, 0.1)
    def stepSizeTuner() = new DualAvgTuner(0.65)
    def massMatrixTuner(): MassMatrixTuner = new StandardMassMatrixTuner
  }

  class Config2 extends Config1 {
    override def massMatrixTuner() =
      new DiagonalMassMatrixTuner(100, 1.5, 100, 100)
  }

  class Config3 extends SamplerConfig {
    val warmupIterations = 0
    val iterations = 1000
    val statsWindow = 100
    def sampler() = new HMCSampler(1)
    def stepSizeTuner() = StaticStepSize(1.0)
    def massMatrixTuner() = StaticMassMatrix(DiagonalMassMatrix(Array(0.1)))
  }

  def variance(seq: Seq[Double]): Double = {
    val mean = seq.sum / seq.size
    seq.map { x =>
      math.pow(x - mean, 2)
    }.sum / (seq.size - 1)
  }

  def main(args: Array[String]): Unit = {
    implicit val rng = RNG.default
    1.to(5).foreach { _ =>
      val samples = List(
        1.4034158867678386, 0.6485888669998515, 3.056932299834341,
        0.9542840021713139, 0.13198897331904033, 0.17432777895325768,
        0.8752461240719891, 1.8217670122950944, 0.7464520193920808,
        0.8913934976791442
      )
      val mu = Normal(0, 1).latent
      val model = Model.observe(samples, LogNormal(mu, 1))

      val trace1 = model.sample(new Config1, 2)
      val trace2 = model.sample(new Config2, 2)

      val var1 = variance(trace1.predict(mu))
      val var2 = variance(trace2.predict(mu))
      val massVar =
        trace2.mass.apply(0).asInstanceOf[DiagonalMassMatrix].elements.apply(0)

      println(f"posterior variance with no adaptation: ${var1}%.3f")
      println(f"posterior variance with adaptation: ${var2}%.3f")
      println(f"adapted variance: ${massVar}%.3f")
      println("---")
      println(trace1.diagnostics)
      println(trace2.diagnostics)
    }
  }
}
