![Mt. Rainier with lenticular clouds (credit: US National Park Service)](rainier.jpg)

# Rainier

[![Build status](https://img.shields.io/travis/stripe/rainier/master.svg)](https://travis-ci.org/stripe/rainier)
[![Coverage status](https://img.shields.io/codecov/c/github/stripe/rainier/master.svg)](https://codecov.io/github/stripe/rainier)
[![Maven Central](https://img.shields.io/maven-central/v/com.stripe/rainier-core_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.stripe/rainier_2.12)
[![Gitter chat](https://badges.gitter.im/com_stripe_rainier/Lobby.png)](https://gitter.im/com_stripe_rainier/Lobby)

Rainier provides an idiomatic, high-performance functional Scala API for bayesian inference via Markov Chain Monte Carlo.

Rainier allows you to describe a complex prior distribution by composing primitive distributions using familiar combinators like `map`, `flatMap`, and `zip`; condition that prior on your observed data; and, after an inference step, sample from the resulting posterior distribution.

Underlying this is a static scalar compute graph with auto-differentiation and very fast CPU-based execution.

It is implemented in pure Scala, with minimal external dependencies and no JNI libs, and as such is convenient to deploy, including to Spark or Hadoop clusters.

Rainier currently provides two samplers: `affine-invariant MCMC`, an ensemble method popularized by the [Emcee](https://github.com/dfm/emcee) package in Python, and `Hamiltonian Monte Carlo`, a gradient-based method used in [Stan](http://mc-stan.org/) and [PyMC3](https://github.com/pymc-devs/pymc3).

Depending on your background, you might think of Rainier as aspiring to be either "Stan, but on the JVM", or "TensorFlow, but for small data".

## Example

Here's what it looks like to fit a simple linear regression with poisson noise and log-normal priors in Rainier:

```scala
val data: List[(Int,Int)] = ???
val model = for {
    slope <- LogNormal(0,1).param
    intercept <- LogNormal(0,1).param
    regression <- Predictor[Int].from{ x => Poisson(x*slope + intercept)}.fit(data)
} yield regression
```

## Performance and Scale

Rainier requires that all of the observations or training data for a given model fit comfortably into RAM on a single machine. It does not make use of GPUs or of SIMD instructions.

Within those constraints, however, it is extremely fast. Rainier takes advantage of knowing all of your data ahead of time by aggressively precomputing as much as it can, which can be a significant practical benefit relative to systems that compile a data-agnostic model. It produces optimized, unboxed, JIT-friendly JVM bytecode for all numerical calculations. This compilation happens in-process and is fast enough for interactive use at a REPL.

For example, on a MacBook Pro, gradient evaluation for [Neal's funnel](/rainier-example/src/main/scala/com/stripe/rainier/example/Funnel.scala) takes under a microsecond, and end-to-end compilation and sampling for 10,000 iterations of HMC with 5 leapfrog steps each takes around 50ms.

Depending on the model, our benchmarking shows Rainier running anywhere from 0.1x to 10x the speed of Stan. Please keep in mind that benchmarking is hard,  micro-benchmarks are often meaningless, and raw performance aside, Stan's sampler implementation is much more sophisticated and much, much, much better tested than Rainier's!

## Documentation

A good starting point is the [Tour of Rainier's Core](docs/tour.md).

If you want to dig deeper, there's a tour of [the underlying compute graph](docs/real.md), as well as some detailed [implementation notes](docs/impl.md).

If you're more familiar with deep learning systems like TensorFlow or PyTorch, you might also be interested in [this brief summary of some of the similarities and differences](docs/dl.md) between DL and MCMC.

## Building

Rainier uses [SBT](https://www.scala-sbt.org/) to build. If you have SBT installed, you can build Rainier and test that it's working by executing `sbt "project rainierExample" run` and then selecting `rainier.example.FitNormal`. You should see output something like this:

```
[info] Running rainier.example.FitNormal
    2.19 |
         |                                 ·           ··
         |               ·           ·  · ·· ·  ···  ·  ·· ·  ·    · · ·
         |                ·   ·   ··························· ··· ·      ·  ···
         |                   · ·············································      ··
    2.10 |             ·····   ···············································  ···
         |          · ·  ···················································· ····      ·
         |·   ·  ·   ···························∘··∘·····························  ·
         |      ··························∘·∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘···················· ··
         |     ·      ··················∘∘∘∘∘∘∘∘∘○○○○∘○○∘∘∘∘∘∘···················· · ·
    2.00 |        ·····················∘∘∘∘∘∘○○○○○○○○○○○○○∘∘∘∘∘∘······················ ·
         |           ··················∘∘∘∘∘∘○○○○○○○○○○○○○∘∘∘∘∘∘···················
         |         ·····················∘∘∘∘∘∘∘○○○○○∘∘○○∘∘∘∘∘∘∘∘················ ·····
         |          ·······················∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘······················· ··  ·
         |       ·· ··························································   ·  ·    ·
    1.90 |          ·  ························································     ·
         |                ············································· ···· ·    · ·
         |                      ·   · ··· ··················· ···· ·  ·    ·
         |                           ··· ·  · ·· ·· ·  ··  · ·· ··
         |                              ·        ··
    1.81 |                                       ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       2.691    2.750    2.810    2.869    2.929    2.988    3.048    3.107    3.167
```

## Contributing

Contributions to Rainier are welcomed. Please note that fatal warnings
are enabled in the CI environment so be sure to fix all warnings before
submitting a pull request for final review.

You can enable fatal warnings locally by either setting
`SCALAC_FATAL_WARNINGS=true` before running SBT or entering
`scalacFatalWarnings := true` in the SBT prompt.

## Using Rainier from SBT or Maven

Rainier is published on sonatype. To use it in your SBT project, you can add the following to your build.sbt:

```scala
libraryDependencies += "com.stripe" %% "rainier-core" % "0.2.1"
```

## Authors

Rainier's primary author is [Avi Bryant](http://twitter.com/avibryant) with major contributions from [Mio Alter](https://twitter.com/mioalter). Many thanks also for contributions and support from:
 * Christian Anderson
 * Alex Beal
 * Travis Brown
 * Peadar Coyle
 * Mike Heaton
 * Grzegorz Kossakowski
 * Roban Kramer
 * Jonny Law
 * Michael Manapat
 * Alan O'Donnell
 * Adam Reese
 * David Rodriguez
 * Andy Scott
 * Aaron Steele
 * Kai(luo) Wang
 * Darren Wilkinson
