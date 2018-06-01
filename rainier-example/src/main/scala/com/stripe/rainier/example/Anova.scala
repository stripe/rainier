/*
Anova.scala

Try doing a one-way ANOVA with random effects model using Rainier

See Gelfand and Smith (1990) for an introduction to MCMC for these kinds of models
and:

https://darrenjw.wordpress.com/2014/12/22/one-way-anova-with-fixed-and-random-effects-from-a-bayesian-perspective/

for a discussion of some practical/conceptual issues.

 */

package com.stripe.rainier.example

import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
import com.stripe.rainier.repl._

object Anova {

  def main(args: Array[String]): Unit = {

    // first simulate some data from an ANOVA model
    implicit val rng = ScalaRNG(3)
    val n = 50 // groups
    val N = 250 // obs per group
    val mu = 5.0 // overall mean
    val sigE = 2.0 // random effect SD
    val sigD = 3.0 // obs SD
    val effects = Vector.fill(n)(sigE * rng.standardNormal)
    val data = effects map (e =>
      Vector.fill(N)(mu + e + sigD * rng.standardNormal))

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
      _ <- RandomVariable.traverse((0 until n).map(addGroup(current, _)))
    } yield current

    // fit model
    println("Model built. Sampling now (will take a long time)...")
    val its = 10000
    val thin = 10000
    val out = model.sample(HMC(5), 1500000, its * thin, thin)
    println("Sampling finished.")

    // process output
    // first some ASCII plots
    println(s"mu (true value $mu):")
    plot1D(out.map(_("Mu")))
    println(s"sigE (true value $sigE):")
    plot1D(out.map(_("sigE")))
    println(s"sigD (true value $sigD):")
    plot1D(out.map(_("sigD")))

    // now some EvilPlots
    import com.cibo.evilplot.geometry.Extent
    import com.stripe.rainier.plot.EvilTracePlot._

    render(traces(out, truth = Map("Mu" -> mu, "sigD" -> sigD, "sigE" -> sigE)),
           "traceplots.png",
           Extent(1200, 1400))
    render(pairs(out, truth = Map("Mu" -> mu, "sigD" -> sigD, "sigE" -> sigE)),
           "pairs.png")
    println("Diagnostic plots written to disk")

  }

}

// eof
