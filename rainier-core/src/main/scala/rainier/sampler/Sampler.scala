package rainier.sampler

import rainier.compute._

trait Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample]
}

case class Sample(accepted: Boolean, parameters: Array[Double])

object Sampler {
  object Default {
    val sampler = Walkers(100)
    val iterations = 10000
    val warmupIterations = 10000
  }

  def gelmanRubin(chains: List[List[Array[Double]]]): List[Double] = {
    val nChains = chains.size
    val nSamples = chains.head.size
    val nParams = chains.head.head.size
    0.until(nParams).toList.map { i =>
      val traces = chains.map { c =>
        c.map { a =>
          a(i)
        }
      }
      val means = traces.map { t =>
        t.sum / nSamples
      }
      val variances =
        traces.zip(means).map {
          case (t, m) =>
            t.map { a =>
              Math.pow(a - m, 2)
            }.sum / (nSamples - 1)
        }
      val w = variances.sum / nChains
      val meanMean = means.sum / nChains
      val b = means.map { m =>
        Math.pow(m - meanMean, 2)
      }.sum / (nChains - 1)
      val v = (1.0 - (1.0 / nSamples)) * w + b
      Math.sqrt(v / w)
    }
  }
}
