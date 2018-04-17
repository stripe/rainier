package rainier.example

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitMixture {
  def model(data: List[Double]) =
    for {
      pSpike <- Uniform(0.0, 1.0).param
      spikeSd <- Uniform(0.0, 0.01).param
      slabSd <- Uniform(0.0, 1.0).param
      _ <- Mixture(
        Map(
          Normal(0.0, spikeSd) -> pSpike,
          Normal(0.0, slabSd) -> (Real.one - pSpike)
        )).fit(data)
    } yield
      Map(
        "P(Spike)" -> pSpike,
        "Spike SD" -> spikeSd,
        "Slab SD" -> slabSd
      )

  def main(args: Array[String]) {
    val rand = new scala.util.Random

    val pSpike = 0.8
    val spikeStddev = 0.003
    val slabStddev = 0.7

    val data = 1
      .to(1000)
      .map { i =>
        val std = if (rand.nextDouble < pSpike) spikeStddev else slabStddev
        rand.nextGaussian * std
      }
      .toList

    val m = model(data)
    //Report.printReport(m, Emcee(1000, 1000, 100))
    Report.printReport(m, Hamiltonian(1000, 1000, 100, 20))
  }
}
