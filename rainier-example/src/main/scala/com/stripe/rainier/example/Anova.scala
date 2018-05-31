/*
Anova.scala

Try doing a one-way ANOVA with random effects model using Rainier

 */

package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object Anova {

  def main(args: Array[String]): Unit = {

    // first simulate some data from an ANOVA model
    val r = new scala.util.Random(0)
    val n = 50 // groups
    val N = 250 // obs per group
    val mu = 5.0 // overall mean
    val sigE = 2.0 // random effect SD
    val sigD = 3.0 // obs SD
    val effects = Vector.fill(n)(sigE * r.nextGaussian)
    val data = effects map (e => Vector.fill(N)(mu + e + sigD * r.nextGaussian))

    // build model
    val prior = for {
      mu <- Normal(0, 100).param
      sigD <- LogNormal(0, 10).param
      sigE <- LogNormal(1, 5).param
    } yield Map("Mu" -> mu, "sigD" -> sigD, "sigE" -> sigE)

    def addGroup(current: Map[String, Real], i: Int) =
      for {
        gm <- Normal(current("Mu"), current("sigE")).param
        _ <- Normal(gm, current("sigD")).fit(data(i))
      } yield gm

    val model = for {
      current <- prior
      _ <- RandomVariable.traverse((0 until n) map (addGroup(current, _)))
    } yield current

    // fit model
    implicit val rng = ScalaRNG(3)
    println("Model built. Sampling now (will take a long time)...")
    val its = 10000
    val thin = 10000
    val out = model.sample(HMC(5), 1500000, its * thin, thin)
    println("Sampling finished.")

    // process output
    // first some ASCII plots
    println(s"mu (true value $mu):")
    println(DensityPlot().plot1D(out map (_("Mu"))).mkString("\n"))
    println(s"sigE (true value $sigE):")
    println(DensityPlot().plot1D(out map (_("sigE"))).mkString("\n"))
    println(s"sigD (true value $sigD):")
    println(DensityPlot().plot1D(out map (_("sigD"))).mkString("\n"))

    // now some EvilPlots
    import com.cibo.evilplot.plot._
    import com.cibo.evilplot.geometry.Extent
    import com.cibo.evilplot.plot.aesthetics.DefaultTheme._
    import com.stripe.rainier.plot._

    val traceplots = Facets(
      EvilTraceplots.traces(out,
                            Map("Mu" -> mu, "sigD" -> sigD, "sigE" -> sigE))
    )
    javax.imageio.ImageIO.write(
      traceplots.render(Extent(1200, 1400)).asBufferedImage,
      "png",
      new java.io.File("traceplots.png"))
    val pairs = Facets(
      EvilTraceplots.pairs(out, Map("Mu" -> mu, "sigD" -> sigD, "sigE" -> sigE))
    )
    javax.imageio.ImageIO.write(
      pairs.render(Extent(1400, 1400)).asBufferedImage,
      "png",
      new java.io.File("pairs.png"))

    println("Diagnostic plots written to disk")

  }

}

// eof
