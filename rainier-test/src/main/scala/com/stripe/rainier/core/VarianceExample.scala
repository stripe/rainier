package com.stripe.rainier.core

import com.stripe.rainier.sampler._

object VarianceExample {
  class Config1 extends SamplerConfig {
    val warmupIterations = 10000
    val iterations = 1000
    val statsWindow = 100
    def sampler() = new HMCSampler(4)
    def stepSizeTuner() = new DualAvgTuner(0.65)
    def massMatrixTuner(): MassMatrixTuner = new StandardMassMatrixTuner
  }

  class Config2 extends Config1 {
    override def massMatrixTuner() =
      new DiagonalMassMatrixTuner(100, 1.1, 100, 100)
  }

  def variance(seq: Seq[Double]): Double = {
    val mean = seq.sum / seq.size
    seq.map { x =>
      math.pow(x - mean, 2)
    }.sum / seq.size
  }

  def main(args: Array[String]): Unit = {
    implicit val rng = RNG.default
    1.to(5).foreach { _ =>
      val samples = Model.sample(LogNormal(0, 1).latent).take(10)
      val mu = Normal(0, 1).latent
      val model = Model.observe(samples, LogNormal(mu, 1))

      val trace1 = model.sample(new Config1)
      val trace2 = model.sample(new Config2)

      val var1 = variance(trace1.predict(mu))
      val var2 = variance(trace2.predict(mu))
      val massVar =
        trace2.mass.apply(0).asInstanceOf[DiagonalMassMatrix].elements.apply(0)

      println(f"posterior variance with no adaptation: ${var1}%.3f")
      println(f"posterior variance with adaptation: ${var2}%.3f")
      println(f"adapted variance: ${massVar}%.3f")
      println("---")
    }
  }
}
