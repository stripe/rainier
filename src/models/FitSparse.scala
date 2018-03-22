package rainier.models

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitSparse {
  def main(args: Array[String]) {
    val r = new scala.util.Random
    val noiseStddev = 1.0
    val data = 1.to(100).map { i =>
      i -> ((r.nextGaussian * noiseStddev) + i)
    }

    val normalModel = for {
      w1 <- Normal(0, 0.01).param
      w2 <- Normal(0, 0.01).param
      w3 <- Normal(0, 0.01).param
      w4 <- Normal(0, 0.01).param
      _ <- Predictor
        .from { i: Int =>
          Normal(i * w1 + i * w2 + i * w3 + i * w4, noiseStddev)
        }
        .fit(data)
    } yield (w1, w2)

    implicit val rng = RNG.default
    println(DensityPlot().plot2D(normalModel.sample()).mkString("\n"))

    val laplaceModel = for {
      w1 <- Laplace(0, 0.01).param
      w2 <- Laplace(0, 0.01).param
      w3 <- Laplace(0, 0.01).param
      w4 <- Laplace(0, 0.01).param
      _ <- Predictor
        .from { i: Int =>
          Normal(i * w1 + i * w2 + i * w3 + i * w4, noiseStddev)
        }
        .fit(data)
    } yield (w1, w2)

    println(DensityPlot().plot2D(laplaceModel.sample()).mkString("\n"))
  }
}
