package rainier.core

import rainier.compute._
import rainier.sampler.RNG
import org.scalatest.FunSuite

class ContinuousTest extends FunSuite {
  implicit val rng = RNG.default

  def check(description: String)(fn: Real => Continuous) = {
    println(description)
    List(0.1, 1.0, 10.0).foreach { trueValue =>
      val trueDist = fn(Real(trueValue))
      val syntheticData = RandomVariable(trueDist.generator).sample().take(1000)
      val sampledData = trueDist.param.sample()
      val model =
        for {
          x <- LogNormal(0, 10).param
          _ <- fn(x).fit(syntheticData)
        } yield x
      val fitValues = model.sample()

      val syntheticMedian =
        syntheticData.toList.sorted.apply(syntheticData.size / 2)
      val sampledMedian = sampledData.toList.sorted.apply(sampledData.size / 2)
      val yErr = (sampledMedian - syntheticMedian) / syntheticMedian

      val fitMean = fitValues.sum / fitValues.size
      val xErr = (fitMean - trueValue) / trueValue

      println(syntheticMedian, sampledMedian, fitMean)

      test(s"y ~ $description, x = $trueValue, y50 within 5%") {
        assert(yErr.abs < 0.05)
      }

      test(s"y ~ $description, x = $trueValue, E(x) within 5%") {
        assert(xErr.abs < 0.05)
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
