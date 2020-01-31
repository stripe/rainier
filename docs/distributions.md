---
id: distributions
title: Distributions
---

All of these distributions are found in `com.stripe.rainier.core`.

In all cases where distributions are parameterized by a `Real`, numeric values will also work.

## Continuous

These distributions all extend `Distribution[Double]`.

They all implement:

* `param: Real`
* `paramVector(k: Int): Vec[Real]`
* `generator: Generator[Double]`

### Normal

* `Normal.standard`
* `Normal(location: Real, scale: Real)`

### Cauchy

* `Cauchy.standard`
* `Cauchy(location: Real, scale: Real)`

### Laplace

* `Laplace.standard`
* `Laplace(location: Real, scale: Real)`

### Gamma

* `Gamma.standard(shape: Real)`
* `Gamma(shape: Real, scale: Real)`
* `Gamma.meanAndScale(mean: Real, scale: Real)`

### Exponential

* `Exponential.standard`
* `Exponential(rate: Real)`

### Beta

* `Beta(a: Real, b: Real)`
* `Beta.meanAndPrecision(mean: Real, precision: Real)`
* `Beta.meanAndVariance(mean: Real, variance: Real)`

### LogNormal

* `LogNormal(location: Real, scale: Real)`

### Uniform

* `Uniform.standard`
* `Uniform(from: Real, to: Real)`

### Mixture

* `Mixture(components: Map[Continuous, Real])`

## Discrete

These distributions all extend `Distribution[Long]`.

They all implement:

* `generator: Generator[Long]`
* `zeroInflated(psi: Real): Distribution[Long]`

### Bernoulli

* `Bernouilli(p: Real)`

### Geometric

* `Geometric(p: Real)`

### NegativeBinomial

* `NegativeBinomial(p: Real, n: Real)`

### Poisson

* `Poisson(lambda: Real)`

### Binomial

* `Binomial(p: Real, k: Real)`

### BetaBinomial

* `BetaBinomial(a: Real, b: Real, k: Real)`
* `BetaBinomial.meanAndPrecision(mean: Real, precision: Real, k: Real)`

### DiscreteMixture

* `DiscreteMixture(components: Map[Discrete, Real])`

## Categorical

A `Categorical[T]` extends `Distribution[T]`.

Like all distributions, it provides `generator` which produces a `Generator[T]`.

It also provides a monad interface:

* `map[U](fn: T => U): Categorical[U]`
* `flatMap[U](fn: T => Categorical[U]): Categorical[U]`
* `zip[U](other: Categorical[U]): Categorical[(T, U)]`

Construction methods:

* `Categorical[T](pmf: Map[T, Real])`
* `Categorical.boolean(p: Real)`
* `Categorical.normalize[T](pmf: Map[T, Real])`
* `Categorical.list[T](seq: Seq[T])`