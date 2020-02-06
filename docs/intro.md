---
id: intro
title: Introduction to Rainier
---

![](/img/rainier-large.jpg)

## Who Rainier is for

Rainier is for building and sampling from Bayesian statistical models. Specifically, it's for building generative models with fixed structure, continuous parameters, and data that can comfortably fit in memory. Generalized linear mixed models (GLMMs), for example, are a very common and flexible class of models that fit that description.

This documentation assumes you have at least some basic familiarity with Bayesian modeling. If you don't have that background, you may struggle with the terminology used here. The single best introduction to the topic is McElreath's [Statistical Rethinking](https://xcelab.net/rm/statistical-rethinking/), and we highly recommend reading it.

We also assume you are at least somewhat familiar with Scala. Perhaps this goes without saying, but: Rainier is a Scala library, and letting you build models in Scala, and run them on the JVM, is one of Rainier's distinguishing features.

## Example

If you just want to get a sense of what building a model in Rainier looks like, here's a simple linear regression:

```scala
val xs: List[(Double, Double, Double)] = ???
val ys: List[Double] = ???

val sigma = Exponential(1).latent
val alpha = Normal(0,1).latent
val betas = Normal(0,1).latentVec(3)

val model = Model.observe(ys, Vec.from(xs).map{
     case (u, v, w) => 
     val mu = alpha + Vec(u,v,w).dot(betas)
     Normal(mu, sigma)
})
```

## This Overview

The rest of this overview is split into four sections. We recommend that you read through each of them before you start working with Rainier. They are:

* [Priors and Random Variables](priors.md)

This introduces the `Distribution` and `Real` types and shows you how to construct random variables from prior distributions.

* [Likelihoods and Observations](likelihoods.md)

This introduces the `Model` and `Trace` types, shows how to condition a model on observations, and how to check your sampling diagnostics.

* [Vectors and Variables](vectors.md)

This introduces the `Vec` type, and shows how to manage larger numbers of parameters and observations.

* [Posteriors and Predictions](posteriors.md)

This introduces the `Generator` type, and shows how to make posterior predictions and decisions from a sampled trace.

## A Note on Performance and Scale

Rainier requires that all of the observations or training data for a given model fit comfortably into RAM on a single machine. It does not make use of GPUs or of SIMD instructions.

Within those constraints, however, it is extremely fast. Rainier takes advantage of knowing all of your data ahead of time by aggressively precomputing as much as it can, which can be a significant practical benefit relative to systems that compile a data-agnostic model. It produces optimized, unboxed, JIT-friendly JVM bytecode for all numerical calculations. This compilation happens in-process and is fast enough for interactive use at a REPL.

Our benchmarking shows that you should expect gradient evaluation to be roughly on par with [Stan](https://mc-stan.org/) (some models will be faster, some will be slower). However, please note that Stan has a more sophisticated dynamic HMC implementation that may well produce more effective samples per second (at least if you ignore the lengthy C++ compilation times).