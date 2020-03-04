package com.stripe.rainier.core

import com.stripe.rainier.sampler._

object VarianceExample {
    class Config1 extends SamplerConfig {
        val warmupIterations = 10000
        val iterations = 1000
        val statsWindow = 100
        def sampler() = new HMCSampler(10)
        def stepSizeTuner() = new DualAvgTuner(0.65)
        def massMatrixTuner(): MassMatrixTuner = new StandardMassMatrixTuner
    }

    class Config2 extends Config1 {
        override def massMatrixTuner() = 
            new DiagonalMassMatrixTuner(100, 1.1, 100, 100)
    }

    def main(args: Array[String]): Unit = {
        implicit val rng = RNG.default
        val samples = Model.sample(LogNormal(0,1).latent).take(10)
        val mu = Normal(0,1).latent
        val model = Model.observe(samples, LogNormal(mu,1))

        val trace1 = model.sample(new Config1)
        val trace2 = model.sample(new Config2)

        println("ESS with no adaptation:" + trace1.diagnostics.head.effectiveSampleSize)
        println("ESS with adaptation:" + trace2.diagnostics.head.effectiveSampleSize)
   }
}