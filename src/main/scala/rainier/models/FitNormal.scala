package rainier.models

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitNormal {
  def model(k: Int) = {
    val r = new scala.util.Random
    val trueMean = 3.0
    val trueStddev = 2.0
    val data = 1.to(k).map { i =>
      (r.nextGaussian * trueStddev) + trueMean
    }

    for {
      mean <- Uniform(0, 10).param
      stddev <- Uniform(0, 10).param
      _ <- Normal(mean, stddev).fit(data)
    } yield (mean, stddev)
  }

  def main(args: Array[String]) {
    implicit val rng = RNG.default
    println(DensityPlot().plot2D(model(1000).sample()).mkString("\n"))
  }
}

object TraceNormal {
  def main(args: Array[String]) {
    val m = FitNormal.model(1000)
    val d = m.density
    val v = Real.variables(d).toList.head
    val g = Gradient.derive(List(v), d).head
    val c = Compiler(List(d, g))
    c.trace

    implicit val rng = RNG.default
    val t1 = System.currentTimeMillis
    val samples =
      m.sample(Hamiltonian(10000, 1000, 100, 100, SampleHMC, 1, 0.01))
    val t2 = System.currentTimeMillis
    println("ms: " + (t2 - t1))
    println("mean: " + (samples.map(_._1).sum / samples.size))
  }
}
