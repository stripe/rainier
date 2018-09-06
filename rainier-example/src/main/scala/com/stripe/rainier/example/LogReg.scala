/*
LogReg.scala

A basic logistic regression model

 */

package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._

object LogReg {

  def main(args: Array[String]): Unit = {

    // first simulate some data from a logistic regression model
    implicit val rng = ScalaRNG(3)
    val N = 1000
    val beta0 = 0.1
    val beta1 = 0.3
    val x = (1 to N) map { i =>
      3.0 * rng.standardNormal
    }
    val theta = x map { xi =>
      beta0 + beta1 * xi
    }
    def expit(x: Double): Double = 1.0 / (1.0 + math.exp(-x))
    val p = theta map expit
    val y = p map (pi => (rng.standardUniform < pi))
    println(y.take(10))
    println(x.take(10))

    // now build Rainier model
    val model = for {
      beta0 <- Normal(0, 5).param
      beta1 <- Normal(0, 5).param
      _ <- Predictor
        .from[Double] { x: Real =>
          {
            val theta = beta0 + beta1 * x
            val p = Real(1.0) / (Real(1.0) + (Real(0.0) - theta).exp)
            Categorical.boolean(p)
          }
        }
        .fit(x zip y)
    } yield Map("b0" -> beta0, "b1" -> beta1)

    // now fit the model
    val its = 10000
    val thin = 5
    val out = model.sample(HMC(5), 10000, its * thin, thin)
    println(out.take(10))

    // now some EvilPlots
    import com.cibo.evilplot.geometry.Extent
    import com.stripe.rainier.plot.EvilTracePlot._

    render(traces(out, truth = Map("b0" -> beta0, "b1" -> beta1)),
           "traceplots.png",
           Extent(1200, 1000))
    render(pairs(out, truth = Map("b0" -> beta0, "b1" -> beta1)), "pairs.png")
    println("Plots written to disk")

  }

}

// eof
