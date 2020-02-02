---
id: model
title: Model
---

`Model` is found in `com.stripe.rainier.core`.

## Instance Methods

* `prior: Model`

Strips away the observations, for ease of checking prior predictions.

* `merge(other: Model): Model`

Combines two models.

* `sample(sampler: Sampler, nChains: Int = 4): Trace`

Run inference using the provided sampler.

* `optimize[T](generator: Generator[T]): T`

Run L-BFGS. Note that this method also accepts values that can be automatically converted via [ToGenerator](trace.md).

## Object Methods

* `observe[Y](ys: Seq[Y], likelihood: Distribution[Y]): Model`
* `observe[Y](ys: Seq[Y], likelihoods: Vec[Distribution[Y]): Model`
