package rainier.models

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitNormal {
  def main(args: Array[String]) {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(1000).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    val model = for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)

    implicit val rng = RNG.default
    println(DensityPlot().plot2D(model.sample()).mkString("\n"))
  }
}
