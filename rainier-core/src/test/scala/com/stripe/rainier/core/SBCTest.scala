package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

trait SBCTest[T] {
  def sbc: SBC[T]
  def samplers: List[(Sampler, Int, Int)]
  def main(args: Array[String]): Unit = {
    samplers.foreach {
      case (s, wi, sd) =>
        println(s"$s with $wi warmup iterations and $sd synthetic data points")
        implicit val rng = ScalaRNG(123L)
        sbc.prepare(s, wi, sd).animate(3)
    }
  }
}

trait SBCSuite extends FunSuite {
  def check[T](sbc: SBC[T],
               samplers: List[(Sampler, Int, Int)],
               rHats: List[Double]) = {
    samplers.zip(rHats).foreach {
      case ((s, wi, sd), rHat) =>
        test(s"$s with $wi warmup iterations and $sd synthetic data points") {
          implicit val rng = ScalaRNG(123L)
          val run = sbc.prepare(s, wi, sd)
          assert(run.rHat == rHat)
        }
    }
  }
}

object NormalSBCTest extends SBCTest[Double] {
  val sbc = SBC(LogNormal(0, 1)) { sd =>
    Normal(0, sd)
  }
  val samplers = List(
    (HMC(5), 1000, 1000)
  )
}
