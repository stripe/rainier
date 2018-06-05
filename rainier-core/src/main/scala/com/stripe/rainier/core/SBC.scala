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

  def animate(sampler: Sampler,
              warmupIterations: Int,
              syntheticSamples: Int,
              logBins: Int = 3)(implicit rng: RNG): Unit = {
    val t0 = System.currentTimeMillis
    val stream = simulate(sampler, warmupIterations, syntheticSamples, logBins)
    val bins = 1 << logBins
    val reps = bins * RepsPerBin

    println(s"\nRunning simulation-based calibration.")

    val lower = binomialQuantile(0.005, reps, 1.0 / bins)
    val upper = binomialQuantile(0.995, reps, 1.0 / bins)
    println("\n" * (bins + 3))
    1.to(reps).foreach { i =>
      val list = stream.take(i).toList
      val timeTaken = System.currentTimeMillis - t0
      val timeRemaining = timeTaken * (reps - i) / i
      plot(list, bins, i, reps, lower, upper, timeRemaining)
    }
  }

  def simulate(sampler: Sampler,
               warmupIterations: Int,
               syntheticSamples: Int,
               logBins: Int = 3)(implicit rng: RNG): Stream[Rep] = {
    require(logBins > 0)
    val bins = 1 << logBins
    require(bins <= Samples)

    val reps = bins * RepsPerBin
    repStream(sampler, warmupIterations, syntheticSamples, bins, reps)
  }

  private def repStream(sampler: Sampler,
                        warmupIterations: Int,
                        syntheticSamples: Int,
                        bins: Int,
                        remaining: Int)(implicit rng: RNG): Stream[Rep] =
    if (remaining == 0)
      Stream.empty
    else {
      val rep =
        repetition(sampler, warmupIterations, syntheticSamples, bins, Trials, 1)
      rep #:: repStream(sampler,
                        warmupIterations,
                        syntheticSamples,
                        bins,
                        remaining - 1)
    }

  private def repetition(sampler: Sampler,
                         warmupIterations: Int,
                         syntheticSamples: Int,
                         bins: Int,
                         trials: Int,
                         thin: Int)(implicit rng: RNG): Rep = {
    val t0 = System.currentTimeMillis
    val (rawRank, rHat, effectiveSampleSize) =
      sample(sampler, warmupIterations, syntheticSamples, thin)
    val ms = System.currentTimeMillis - t0

    if (trials > 1 && effectiveSampleSize < Samples) {
      val newThin =
        Math
          .ceil(Samples.toDouble / effectiveSampleSize)
          .toInt
      repetition(sampler,
                 warmupIterations,
                 syntheticSamples,
                 bins,
                 trials - 1,
                 newThin)
    } else {
      val rank = (rawRank * bins) / Samples
      Rep(rank, rHat, thin, effectiveSampleSize, ms)
    }
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
                                                      true,
                                                      thin)

    val maxRHat = diag.map(_.rHat).max
    val minEffectiveSampleSize = diag.map(_.effectiveSampleSize).min
    val rawRank = samples.tail.count { n =>
      n < trueOutput
    }
    (rawRank, maxRHat, minEffectiveSampleSize)
  }

  private def plot(list: List[Rep],
                   bins: Int,
                   rep: Int,
                   reps: Int,
                   lower: Int,
                   upper: Int,
                   millisRemaining: Long): Unit = {
    println("\u001b[1000D") //move left
    println(s"\u001b[${bins + 6}A") //move up
    val remaining = formatMillis(millisRemaining)
    val maxRHat = formatRHat(list.map(_.rHat).max)
    val maxThin = list.map(_.thin).max
    val totalEffectiveSamples = list.map(_.effectiveSampleSize).sum
    val totalTime = list.map(_.ms).sum
    val samplesPerSecond = formatRate(
      (totalEffectiveSamples * 1000).toDouble / totalTime)
    println(s"Repetition $rep/$reps. Estimated time remaining: $remaining")
    println(
      s"Samples/sec: $samplesPerSecond. Max rHat: $maxRHat. Max thinning factor: $maxThin   ")
    println("99% of bins should end up between the [ and ] quantile markers\n")

    val binMap = list.groupBy(_.rank).mapValues(_.size)
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

  private def formatRHat(rHat: Double): String =
    "%.3f".format(rHat)

  private def formatRate(rate: Double): String =
    if (rate > 1)
      rate.toInt.toString
    else
      "%.3f".format(rate)

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

object SBC {
  /*
  fn is a function that takes a Seq[Real]
  specifying the values of all the parameters (one for each Continuous in the prior)
  and returns a (Distribution[T], Real) which is a pair of values:
  1) a distribution describing the likelihood of the observed data, given the parameter values,
  2) the parameter value or summary stat we're calibrating on
   */
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

  val Samples = 1024
  val Chains = 4
  val RepsPerBin = 40
  val Trials = 5

  case class Rep(rank: Int,
                 rHat: Double,
                 thin: Int,
                 effectiveSampleSize: Double,
                 ms: Long)
}
