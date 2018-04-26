package rainier.core

import rainier.compute._
import rainier.sampler._
import org.scalatest.FunSuite

class ContinuousTest extends FunSuite {
  implicit val rng = RNG.default

  def check(description: String)(fn: Real => Continuous) = {
    println(description)
    List(0.1, 1.0, 2.0).foreach { trueValue =>
      List(Hamiltonian(10000, 1000, 100, SampleHMC, 1), Emcee(100, 100, 100))
        .foreach { sampler =>
          val trueDist = fn(Real(trueValue))
          val syntheticData =
            RandomVariable(trueDist.generator).sample().take(1000)
          val sampledData = trueDist.param.sample()
          val model =
            for {
              x <- LogNormal(0, 10).param
              _ <- fn(x).fit(syntheticData)
            } yield x
          val fitValues = model.sample()

          val syntheticMean = syntheticData.sum / syntheticData.size
          val syntheticStdDev = Math.sqrt(syntheticData.map { n =>
            Math.pow(n - syntheticMean, 2)
          }.sum / syntheticData.size)
          val sampledMean = sampledData.sum / sampledData.size
          val yErr = (sampledMean - syntheticMean) / syntheticStdDev

          val fitMean = fitValues.sum / fitValues.size
          val xErr = (fitMean - trueValue) / trueValue

          val className = sampler.getClass.getName.split("[.]").last
          test(
            s"$className: y ~ $description, x = $trueValue, E(y) within 0.1 SD") {
            assert(yErr.abs < 0.1)
          }

          test(s"$className: y ~ $description, x = $trueValue, E(x) within 5%") {
            assert(xErr.abs < 0.05)
          }
        }
    }
  }

  check("Normal(x,x)") { x =>
    Normal(x, x)
  }

  check("LogNormal(x,x)") { x =>
    LogNormal(x, x)
  }

  check("Exponential(x)") { x =>
    Exponential(x)
  }

  check("Uniform(x,x*2)") { x =>
    Uniform(x, x * 2)
  }

  check("Laplace(x,x)") { x =>
    Laplace(x, x)
  }
}
