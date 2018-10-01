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
    List(0.45334921294361863, 0.35466247963716674, 0.4507827014261609,
      0.4507827014261609, 0.4507827014261609, 0.3527753110309034,
      0.4478239369797434, 0.4478239369797434, 0.4478239369797434,
      0.3909152589516095, 0.4174530489927148, 0.4189986682160813,
      0.4056458867161374, 0.4336698829917337, 0.4336698829917337,
      0.37501550699756414, 0.37501550699756414, 0.37501550699756414,
      0.3894242721548835, 0.42143316839906053, 0.375296176741582)
  val description = "Normal(x, 1) with Uniform(0, 1) prior"
}

object SBCLogNormal extends SBCModel {
  def sbc =
    SBC[Double, Continuous](LogNormal(0, 1))((x: Real) => LogNormal(x, x))
  def goldset =
    List(0.47655071024953893, 0.47655071024953893, 0.47655071024953893,
      0.4518921004798341, 0.4518921004798341, 0.460047853361003,
      0.47882788323834014, 0.44423393783509674, 0.44423393783509674,
      0.47597230933415974, 0.47708879007966615, 0.47708879007966615,
      0.47969020744327384, 0.4516436419537544, 0.4516436419537544,
      0.4516436419537544, 0.45587196189979023, 0.44144408924078793,
      0.4713105740856453, 0.4649363307308761, 0.4306363328580164)
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
    List(0.501091076498314, 0.5126255001250232, 0.5126255001250232,
      0.5126255001250232, 0.4691026567260245, 0.495185379885554,
      0.5162374462991528, 0.5162374462991528, 0.5052532989696195,
      0.5096413191542593, 0.5096413191542593, 0.49887067129172724,
      0.5060766721055809, 0.5101868989254492, 0.47715648657288423,
      0.4768882923479403, 0.4936254108750285, 0.4936254108750285,
      0.5172738564628032, 0.49798577788116216, 0.5026233859119448)
  val description = "Bernoulli(x) with Uniform(0, 1) prior"
}

object SBCBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Binomial(x, 10))
  def goldset =
    List(0.3841973973242428, 0.3841973973242428, 0.3841973973242428,
      0.3818581365224068, 0.3818581365224068, 0.3818581365224068,
      0.3732611394492603, 0.3730945841474085, 0.3852046648117591,
      0.3852046648117591, 0.37841671902322194, 0.38386801502243656,
      0.38386801502243656, 0.38386801502243656, 0.3832021671702386,
      0.3840982340598172, 0.3840982340598172, 0.3840982340598172,
      0.3841510715760621, 0.3786562308220189, 0.3786562308220189)
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
    List(1, 3, 6, 11, 7, 10, 8, 19, 7, 4, 14, 19, 6, 14, 6, 8, 12, 8, 14, 13, 4)
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
