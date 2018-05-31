package com.stripe.rainier.core

import com.stripe.rainier.sampler._
import com.stripe.rainier.compute._

final case class SBC[T](priorGenerators: Seq[Generator[Double]],
                        priorParams: Seq[Real],
                        posterior: RandomVariable[(Distribution[T], Real)]) {
  import SBC._

  require(priorParams.forall(_.variables.size == 1))
  val priorGenerator = Generator.traverse(priorGenerators)
  val emptyEvaluator = new Evaluator(Map.empty)

  def simulate(sampler: Sampler,
               warmupIterations: Int,
               syntheticSamples: Int,
               logBins: Int)(implicit rng: RNG): Either[Double, Stream[Int]] = {
    val initialThin = 2
    val (_, maxRHat, minEffectiveSampleSize) =
      sample(sampler, warmupIterations, syntheticSamples, initialThin)
    if (maxRHat > MaxRHat)
      Left(maxRHat)
    else {
      val thin =
        if (minEffectiveSampleSize < Samples)
          Math
            .ceil(initialThin.toDouble * Samples / minEffectiveSampleSize)
            .toInt
        else
          initialThin

      Right(
        rankStream(sampler, warmupIterations, syntheticSamples, thin, logBins))
    }
  }

  private def rankStream(sampler: Sampler,
                         warmupIterations: Int,
                         syntheticSamples: Int,
                         thin: Int,
                         logBins: Int)(implicit rng: RNG): Stream[Int] =
    rank(sampler, warmupIterations, syntheticSamples, thin, logBins) #::
      rankStream(sampler, warmupIterations, syntheticSamples, thin, logBins)

  private def rank(sampler: Sampler,
                   warmupIterations: Int,
                   syntheticSamples: Int,
                   thin: Int,
                   logBins: Int)(implicit rng: RNG): Int =
    sample(sampler, warmupIterations, syntheticSamples, thin)._1 /
      (Samples / (1 << logBins))

  private def sample(sampler: Sampler,
                     warmupIterations: Int,
                     syntheticSamples: Int,
                     thin: Int)(implicit rng: RNG): (Int, Double, Double) = {
    val trueValues = priorGenerator.get(rng, emptyEvaluator)
    implicit val trueEval = new Evaluator(priorParams.zip(trueValues).toMap)
    val trueOutput = posterior.map(_._2).get

    val syntheticValues =
      posterior.map(_._1.generator.repeat(syntheticSamples)).get
    val model = posterior.flatMap {
      case (d, r) =>
        d.fit(syntheticValues).map { _ =>
          r
        }
    }
    val (samples, diag) = model.sampleWithDiagnostics(sampler,
                                                      Chains,
                                                      warmupIterations,
                                                      (Samples / Chains) * thin,
                                                      thin)

    val maxRHat = diag.map(_.rHat).max
    val minEffectiveSampleSize = diag.map(_.effectiveSampleSize).min
    val rawRank = samples.count { n =>
      n < trueOutput
    }
    (rawRank, maxRHat, minEffectiveSampleSize)
  }
}

object SBC {
  def apply[T](priors: Seq[Continuous])(
      fn: Seq[Real] => (Distribution[T], Real)): SBC[T] = {
    val priorParams = priors.map(_.param)
    val priorGenerators = priors.map(_.generator)
    val posterior = RandomVariable.traverse(priorParams).map(fn)
    SBC(priorGenerators, priorParams.map(_.value), posterior)
  }

  def apply[T](prior: Continuous)(fn: Real => Distribution[T]): SBC[T] =
    apply(List(prior)) { l =>
      (fn(l.head), l.head)
    }

  val Samples = 123
  val Chains = 4
  val MaxRHat = 1.1
}
