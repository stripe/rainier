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
    List(0.47655071024953893, 0.47655071024953893, 0.47655071024953893,
      0.4518921004798341, 0.4518921004798341, 0.460047853361003,
      0.47882788323834014, 0.44423393783509674, 0.44423393783509674,
      0.47597230933415974, 0.47708879007966615, 0.47708879007966615,
      0.47969020744327384, 0.4516436419537544, 0.4516436419537544,
      0.4516436419537544, 0.45587196189979023, 0.44144408924078793,
      0.4713105740856453, 0.4649363307308761, 0.4306363328580164)
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
    List(0.4765506535030612, 0.4765506535030612, 0.4765506535030612,
      0.45143004976923734, 0.45143004976923734, 0.4601786676153553,
      0.47862606032999144, 0.44375621459360326, 0.44375621459360326,
      0.4762279782032409, 0.47648928481018665, 0.47648928481018665,
      0.47908951234613467, 0.4511114715288357, 0.4511114715288357,
      0.4511114715288357, 0.45603047430101173, 0.4415531094348786,
      0.4716294722686012, 0.46452706160157037, 0.4305693327124825)
  val description = "Bernoulli(x) with Uniform(0, 1) prior"
}

object SBCBinomial extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Binomial(x, 10))
  def goldset =
    List(0.47655066813238467, 0.47655066813238467, 0.47655066813238467,
      0.4518384392860516, 0.4518384392860516, 0.4600618675850346,
      0.4788055372117351, 0.4441777454257175, 0.4441777454257175,
      0.4760012458228717, 0.4770206379033649, 0.4770206379033649,
      0.47961976349049357, 0.45158033067961706, 0.45158033067961706,
      0.45158033067961706, 0.45588886963332825, 0.4414566304407526,
      0.4713482158940865, 0.4648899924131283, 0.4306267635761126)
  val description = "Binomial(x, 10) with Uniform(0, 1) prior"
}

object SBCGeometric extends SBCModel {
  def sbc =
    SBC[Int, Discrete](Uniform(0, 1))((x: Real) => Geometric(x))
  def goldset =
    List(0, 1, 4, 0, 0, 2, 2, 2, 2, 1, 1, 1, 0, 1, 1, 0, 0, 0, 2, 0, 2)
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
    List(0.01740705314050643, 0.01740705314050643, 0.01577939565305407,
      0.018484616196969783, 0.01566633273808055, 0.016786368083366688,
      0.016786368083366688, 0.016786368083366688, 0.0159005678266814,
      0.01722442452514522, 0.01798089099578752, 0.015278224954738944,
      0.018598330773273546, 0.018598330773273546, 0.018598330773273546,
      0.018598330773273546, 0.015603535962740101, 0.015603535962740101,
      0.01696544994882881, 0.018151114257140336, 0.015884245690092228)
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
