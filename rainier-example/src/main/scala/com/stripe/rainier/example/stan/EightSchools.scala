package com.stripe.rainier.example.stan

//from the stan stat_comp_benchmarks

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object EightSchools {
  def model(ys: Seq[Int], sigmas: Seq[Int]): RandomVariable[Map[Int, Real]] =
    for {
      mu <- Normal(0, 5).param
      tau <- Cauchy(0, 5).param
      thetas <- RandomVariable.traverse(
        ys.zip(sigmas).map {
          case (y, sigma) =>
            fitTheta(mu, tau, y, sigma)
        }
      )
    } yield thetas.zipWithIndex.map { case (t, i) => i -> t }.toMap

  def fitTheta(mu: Real, tau: Real, y: Int, sigma: Int): RandomVariable[Real] =
    for {
      theta <- Normal(mu, tau.abs).param
      _ <- Normal(theta, sigma).fit(y.toDouble)
    } yield theta

  def main(args: Array[String]): Unit = {
    val m = model(ys, sigmas)
    val t0 = System.nanoTime
    val params = m.sample(HMC(5), warmups, samples, thin)
    val t1 = System.nanoTime
    val seconds = (t1 - t0) / 1e9
    println(s"$samples samples in $seconds seconds")
    0.until(ys.size).foreach { i =>
      val mean = params.map { _(i) }.sum / params.size
      println(s"theta[$i] = $mean")
    }
  }

  val warmups = 1000
  val samples = 1000000
  val thin = 1000
  val ys = List(28, 8, -3, 7, -1, 1, 18, 12)
  val sigmas = List(15, 10, 16, 11, 9, 11, 10, 18)
}
