![Mt. Rainier with lenticular clouds (credit: US National Park Service)](rainier.jpg)

# Rainier

Rainier provides a high-performance Scala API for bayesian inference via Markov Chain Monte Carlo.

Rainier supports fixed-structure generative models with continuous parameters. Models are built using idiomatic, functional Scala to compose primitive distributions and mathematical transformations. Once built, you can condition the model on observed data to infer a posterior distribution over the parameter values, and then use that posterior distribution to generate decisions and predictions.

Underlying this high-level API is a static, TensorFlow-style compute graph with auto-differentiation and very fast CPU-based execution.

Rainier is implemented in pure Scala, with minimal external dependencies and no JNI libs, and as such is convenient to deploy, including to Spark or Hadoop clusters.

Inference is based on variants of the `Hamiltonian Monte Carlo` sampler; similar to, and targeting the same types of models as, both [Stan](http://mc-stan.org/) and [PyMC3](https://github.com/pymc-devs/pymc3). Depending on your background, you might think of Rainier as aspiring to be either "Stan, but on the JVM", or "TensorFlow, but for small data".

## Example

Here's what it looks like to fit a simple linear regression in Rainier:

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

## Getting Started and Documentation

There's a detailed overview and reference documentation at [rainier.fit](https://rainier.fit/docs/intro).

## Performance and Scale

Rainier requires that all of the observations or training data for a given model fit comfortably into RAM on a single machine. It does not make use of GPUs or of SIMD instructions.

Within those constraints, however, it is extremely fast. Rainier takes advantage of knowing all of your data ahead of time by aggressively precomputing as much as it can, which can be a significant practical benefit relative to systems that compile a data-agnostic model. It produces optimized, unboxed, JIT-friendly JVM bytecode for all numerical calculations. This compilation happens in-process and is fast enough for interactive use at a REPL.

For example, on a MacBook Pro, gradient evaluation for Neal's funnel takes under a microsecond, and end-to-end compilation and sampling for 10,000 iterations of HMC with 5 leapfrog steps each takes around 50ms.

Our benchmarking shows that you should expect gradient evaluation to be roughly on par with Stan (some models will be faster, some will be slower). However, please note that Stan currently has a more sophisticated dynamic HMC implementation that may well produce more effective samples per second (at least if you ignore the lengthy C++ compilation times).

## Authors

Rainier's primary author is [Avi Bryant](http://twitter.com/avibryant) with major contributions from [Mio Alter](https://twitter.com/mioalter) and Andrew Metcalf. Many thanks also for contributions and support from:
 * Christian Anderson
 * Alex Beal
 * Arthur Bit-Monnot
 * Travis Brown
 * Peadar Coyle
 * Mike Heaton
 * Grzegorz Kossakowski
 * Roban Kramer
 * Jonny Law
 * Michael Manapat
 * Alan O'Donnell
 * Adam Reese
 * Sam Ritchie
 * David Rodriguez
 * Andy Scott
 * Aaron Steele
 * Andrew Valencik
 * Kai(luo) Wang
 * Darren Wilkinson
