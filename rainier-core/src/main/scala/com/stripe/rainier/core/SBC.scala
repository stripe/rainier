package com.stripe.rainier.core

import com.stripe.rainier.sampler._
import com.stripe.rainier.compute._

//implements Simulation-Based Calibration from https://arxiv.org/abs/1804.06788
final case class SBC[T](priorGenerators: Seq[Generator[Double]],
                        priorParams: Seq[Real],
                        posterior: RandomVariable[(Distribution[T], Real)]) {

  import SBC._

  require(priorParams.forall(_.variables.size == 1))
  val priorGenerator = Generator.traverse(priorGenerators)
  val emptyEvaluator = new Evaluator(Map.empty)

  def prepare(sampler: Sampler, warmupIterations: Int, syntheticSamples: Int)(
      implicit rng: RNG): Run = {
    val initialThin = 2
    val (_, maxRHat, minEffectiveSampleSize) =
      sample(sampler, warmupIterations, syntheticSamples, initialThin)
    val thin =
      if (minEffectiveSampleSize < Samples)
        Math
          .ceil(initialThin.toDouble * Samples / minEffectiveSampleSize)
          .toInt
      else
        initialThin
    Run(sampler, warmupIterations, syntheticSamples, thin, maxRHat)
  }

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
    val rawRank = samples.tail.count { n =>
      n < trueOutput
    }
    (rawRank, maxRHat, minEffectiveSampleSize)
  }

  case class Run(sampler: Sampler,
                 warmupIterations: Int,
                 syntheticSamples: Int,
                 thin: Int,
                 rHat: Double)(implicit rng: RNG) {

    def animate(logBins: Int): Unit = {
      val t0 = System.currentTimeMillis
      val stream = simulate(logBins)
      val bins = 1 << logBins
      val reps = bins * RepsPerBin

      println(
        s"\nRunning simulation-based calibration. rHat: $rHat, thinning factor: $thin")

      val lower = binomialQuantile(0.005, reps, 1.0 / bins)
      val upper = binomialQuantile(0.995, reps, 1.0 / bins)
      println("\n" * (bins + 1))
      1.to(reps).foreach { i =>
        val list = stream.take(i).toList
        val timeTaken = System.currentTimeMillis - t0
        val timeRemaining = timeTaken * (reps - i) / i
        plot(list, bins, i, reps, lower, upper, timeRemaining)
      }
    }

    def simulate(logBins: Int): Stream[Int] = {
      require(logBins > 0)
      val bins = 1 << logBins
      require(bins <= Samples)

      val reps = bins * RepsPerBin
      rankStream(bins, reps)
    }

    private def rankStream(bins: Int, remaining: Int): Stream[Int] =
      if (remaining == 0)
        Stream.empty
      else {
        val (rawRank, _, _) =
          sample(sampler, warmupIterations, syntheticSamples, thin)
        val rank = (rawRank * bins) / Samples
        rank #:: rankStream(bins, remaining - 1)
      }

    private def plot(list: List[Int],
                     bins: Int,
                     rep: Int,
                     reps: Int,
                     lower: Int,
                     upper: Int,
                     millisRemaining: Long): Unit = {
      println("\u001b[1000D") //move left
      println(s"\u001b[${bins + 3}A") //move up
      val remaining = formatMillis(millisRemaining)
      println(s"Repetition $rep/$reps. Estimated time remaining: $remaining")
      val binMap = list.groupBy(identity).mapValues(_.size)
      val binCounts = 0.until(bins).map { i =>
        binMap.getOrElse(i, 0)
      }
      binCounts.foreach { n =>
        if (n < lower || n > upper)
          print("\u001b[31m") //red
        else
          print("\u001b[32m") //green
        print(paddedBar(n, lower))
        print("[")
        print(paddedBar(n - lower, upper - lower))
        print("]")
        println(paddedBar(n - upper, n - upper))
        print("\u001b[0m") //reset color
      }
    }

    private def paddedBar(fill: Int, width: Int): String =
      if (width <= 0)
        ""
      else if (fill <= 0)
        " " * width
      else
        "#" * (fill.min(width)) + (" " * (width - fill))

    private def formatMillis(millis: Long): String = {
      val s = millis / 1000
      "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    private def binomialQuantile(q: Double, n: Int, p: Double): Int = {
      var cmf = 0.0
      var k = -1
      while (cmf < q) {
        k += 1
        val logPmf = Binomial(p, n).logDensity(k)
        cmf += emptyEvaluator.toDouble(logPmf.exp)
      }
      k
    }
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

  val Samples = 128
  val Chains = 4
  val RepsPerBin = 40
  val MaxRHat = 1.1
}
