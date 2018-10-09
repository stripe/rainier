package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

trait SBCModel {
  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def sbc: SBC[_, _]
  val sampler: Sampler = HMC(1)
  val warmupIterations: Int = 10000
  val syntheticSamples: Int = 1000
  val nSamples: Int = 10
  def main(args: Array[String]): Unit = {
    sbc.animate(sampler, warmupIterations, syntheticSamples)
    println(s"\nnew goldset:")
    println(s"$samples")
    println(
      s"If this run looks good, please update the goldset in your SBCModel")
  }
  val samples: List[_] = sbc.posteriorSamples(goldset.size)
  def goldset: List[_]
  val description: String
}

/** Continuous **/
object SBCUniformNormal extends SBCModel {
  def sbc = SBC[Double, Continuous](Uniform(0, 1))((x: Real) => Normal(x, 1))
  override val sampler = HMC(2)
  def goldset =
    List(0.3501402791470262, 0.3501402791470262, 0.3501402791470262,
      0.3957013241213891, 0.351640706229258, 0.40631693523734325,
      0.40631693523734325, 0.3262598114570841, 0.3262598114570841,
      0.42424828165512385, 0.3473455814372044, 0.37995711394205556,
      0.3525219324745592, 0.2814894618733758, 0.44230112017931167,
      0.3294586660596213, 0.3976153915945299, 0.3976153915945299,
      0.35291275234704833, 0.35291275234704833, 0.35598173705405856)
  val description = "Normal(x, 1) with Uniform(0, 1) prior"
}

object SBCLogNormal extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset =
    List(0.1303910109992433, 0.1280922827094485, 0.13144664422685157,
      0.13144664422685157, 0.1259190226694656, 0.12653277627025097,
      0.1301183001837163, 0.12923234166506364, 0.12854626704011574,
      0.12854626704011574, 0.12487741948599572, 0.12764309480267635,
      0.12788983507936355, 0.1287127541653713, 0.1287127541653713,
      0.1287127541653713, 0.1287127541653713, 0.127574207214845,
      0.12416047864273923, 0.12416047864273923, 0.13000937460401513)
  val description = "LogNormal(x, x) with LogNormal(0, 1) prior"
}

/**
  * Note: SBCExponential and SBCLaplace are made-up goldsets. SBC on these is wildly slow.
  */
object SBCExponential extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Exponential(x))
  def goldset =
    List(0.4265683630081846, 0.5189050953677488, 0.49924580068677044,
      0.3879796746979638, 0.4341114186909587, 0.4341114186909587,
      0.46249827359385365, 0.5153090873282923, 0.44657645973736837,
      0.4818619620463942, 0.43936322908013287, 0.4437800418959559,
      0.367162365055694, 0.367162365055694, 0.367162365055694,
      0.367162365055694, 0.367162365055694, 0.4330711704882621,
      0.4330711704882621, 0.5628095742189261, 0.45466790056406947)
  val description = "Exponential(x) with LogNormal(0, 1) prior"
}

object SBCLaplace extends SBCModel {
  def sbc = SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => Laplace(x, x))
  def goldset =
    List(0.4265683630081846, 0.5189050953677488, 0.49924580068677044,
      0.3879796746979638, 0.4341114186909587, 0.4341114186909587,
      0.46249827359385365, 0.5153090873282923, 0.44657645973736837,
      0.4818619620463942, 0.43936322908013287, 0.4437800418959559,
      0.367162365055694, 0.367162365055694, 0.367162365055694,
      0.367162365055694, 0.367162365055694, 0.4330711704882621,
      0.4330711704882621, 0.5628095742189261, 0.45466790056406947)
  val description = "Laplace(x, x) with LogNormal(0, 1) prior"
}

/** Discrete **/
object SBCBernoulli extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Bernoulli(x))
  def goldset =
    List(0.3563765920414142, 0.3718023304089825, 0.3718023304089825,
      0.3785422747406583, 0.3567960673274999, 0.3619615226086536,
      0.3609381659564787, 0.3718792706091971, 0.3531479223549662,
      0.35836755664395503, 0.39326078885067545, 0.3429922615208297,
      0.3429922615208297, 0.38630946227383905, 0.38630946227383905,
      0.38630946227383905, 0.38630946227383905, 0.3739836590175712,
      0.38104724579070404, 0.38280273670968995, 0.3616792352308196)
  val description = "Bernoulli(x) with Uniform(0, 1) prior"
}

object SBCBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Binomial(x, 10))
  def goldset =
    List(0.38414085296723444, 0.38414085296723444, 0.38414085296723444,
      0.3816145812101797, 0.3816145812101797, 0.3816145812101797,
      0.37332973292871396, 0.37364722065467604, 0.3854639569497262,
      0.3907396468297855, 0.37749334076190105, 0.38407820039622315,
      0.38407820039622315, 0.38407820039622315, 0.38291432174100637,
      0.38390819506266094, 0.38390819506266094, 0.38390819506266094,
      0.3838913526262309, 0.37849960687701784, 0.37849960687701784)
  val description = "Binomial(x, 10) with Uniform(0, 1) prior"
}

object SBCGeometric extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Geometric(x))
  def goldset =
    List(0.38401359709585026, 0.38556445728441135, 0.38556445728441135,
      0.3913043853313101, 0.377454854969508, 0.3802564348075519,
      0.37981412104022816, 0.386557995159251, 0.3751460335876146,
      0.3779463106487424, 0.3999553568830646, 0.3999553568830646,
      0.3999553568830646, 0.3906035980224451, 0.3906035980224451,
      0.3906035980224451, 0.3906035980224451, 0.3891467805726965,
      0.3927631490155504, 0.3942988962341207, 0.3805771575303083)
  val description = "Geometric(x) with Uniform(0, 1) prior"
}

object SBCGeometricZeroInflated extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) =>
      Geometric(.3).zeroInflated(x))
  def goldset =
    List(0, 1, 0, 1, 0, 0, 0, 0, 10, 11, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0)
  val description = "Geometric(.3).zeroInflated(x) with Uniform(0, 1) prior"
}

object SBCNegativeBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => NegativeBinomial(x, 10))
  def goldset =
    List(0.38644349802542166, 0.38644349802542166, 0.38644349802542166,
      0.38453256099270533, 0.38453256099270533, 0.38453256099270533,
      0.3778845057918654, 0.377959550988004, 0.3873191442934522,
      0.3873191442934522, 0.3818063003527945, 0.38623315609417286,
      0.38623315609417286, 0.38623315609417286, 0.3855873063788613,
      0.38631772131802333, 0.38631772131802333, 0.38631772131802333,
      0.3863381119962115, 0.38203456573515593, 0.38203456573515593)
  val description = "NegativeBinomial(x, 10) with Uniform(0, 1) prior"
}

object SBCBinomialPoissonApproximation extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 0.04))((x: Real) => Binomial(x, 200))
  def goldset =
    List(0.01535531864853668, 0.01535531864853668, 0.01535531864853668,
      0.015298258177048994, 0.015298258177048994, 0.015074569332312245,
      0.015073095939794659, 0.0146904575577443, 0.015320361363874114,
      0.015320361363874114, 0.015320361363874114, 0.014880539519888873,
      0.015041394294566584, 0.015042401358899874, 0.015042401358899874,
      0.015648208877512036, 0.01500052671730356, 0.01500052671730356,
      0.01500052671730356, 0.015173941568466989, 0.015213369301112573)
  val description =
    "Poisson approximation to Binomial: Binomial(x, 200) with Uniform(0, 0.04) prior"
}

object SBCBinomialNormalApproximation extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0.4, 0.6))((x: Real) => Binomial(x, 300))
  def goldset =
    List(1, 6, 10, 234, 10, 3, 4, 9, 8, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3)
  val description =
    "Normal approximation to Binomial: Binomial(x, 200) with Uniform(0.4, 0.6) prior"
}
