---
id: model
title: Model and Trace
---

`Model` and `Trace` are both found in `com.stripe.rainier.core`.

## Model

### Instance Methods

* `prior: Model`

Strips away the observations, for ease of checking prior predictions.

* `merge(other: Model): Model`

Combines two models.

* `sample(config: SamplerConfig, nChains: Int = 4): Trace`

Run inference using the provided sampler configuration.

* `optimize[T](value: Generator[T]): T`

Run L-BFGS. Note that this method will accept non-`Generator` values, and automatically wrap them with `Generator()`, if possible.

### Object Methods

* `observe[Y](ys: Seq[Y], likelihood: Distribution[Y]): Model`
* `observe[Y](ys: Seq[Y], likelihoods: Vec[Distribution[Y]): Model`

## Trace

* `diagnostics: List[Trace.Diagnostics]`

Produce a list of `Diagnostics(rHat: Double, effectiveSampleSize: Double)`, one for each parameter. Requires chains > 1.

* `thin(n: Int): Trace`

Keep every n'th sample in each chain.

* `predict[T](value: Generator[T]): List[T]`

Generate one value from `generator` from each sample in the trace. Like `optimize`, this will automatically convert values into `Generator` where possible.

* `mean[N:Numeric](value: Generator[N]): Double`

For any numeric `Generator`, compute the posterior expectation.