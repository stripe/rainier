---
id: model
title: Model and Fn
---

`Model` is found in `com.stripe.rainier.core`, and `Fn` in `com.stripe.rainier.compute`.

## Model

### Instance Methods

* `prior: Model`

Strips away the observations, for ease of checking prior predictions.

* `merge(other: Model): Model`

Combines two models.

* `sample(sampler: Sampler, nChains: Int = 4): Trace`

Run inference using the provided sampler.

* `optimize[T, U](value: T): U`

Run L-BFGS. `value: T` must be something that can be converted to a `Generator[U]`. See the [Trace and Generator](trace.md) documentation for more.

### Object Methods

* `observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model`
* `observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model`
* `observe[X, Y](xs: Seq[X], ys: Seq[Y], fn: Fn[X, Distribution[Y]]): Model`

## Fn

