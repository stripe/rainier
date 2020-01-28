---
id: likelihoods
title: Likelihoods and Observations 
---

```scala mdoc:invisible
import com.stripe.rainier.core._
import com.stripe.rainier.plot._
import Jupyter._
```

The [previous section](priors.md) briefly showed you how to create random variables from prior distributions. In this section, we'll show you how to use distributions as likelihoods, to update our beliefs about those random variables based on real-world data.

## Counting your Chickens

Imagine that you keep chickens, and you'd like to know the rate at which they lay eggs. You've kept track of how many you've gotten each day for the last month:

```scala mdoc:silent
val eggs = List[Long](45, 52, 45, 47, 41, 42, 44, 42, 46, 38, 36, 35, 41, 48, 42, 29, 45, 43, 45, 40, 42, 53, 31, 48, 40, 45, 39, 29, 45, 42)

```

A simple model for this is to assume that the number of eggs per day is Poisson-distributed, with some mean `lambda` that we don't know. Since we don't know it, we should create a random variable for it. It's traditional to use a `Gamma` prior here.

```scala mdoc:to-string
val lambda = Gamma(0.5, 100).param
```

Sanity check: we can see that `lambda` is between (0, infinity), which is good, because it doesn't make sense to lay a negative number of eggs.

We can also confirm with a quick plot that our prior is very broad, and not very helpful for making predictions.

```scala
show("lambda", density(Model.sample(lambda)))
```

```scala mdoc:evilplot:assets/lambda.png
show("lambda", density(Model.sample(lambda)))
```

In fact, if we've been paying any attention to our chickens at all, we could probably have come up with a much more informative prior. But that's ok; we'll stick with the less informative one, and let the data do the talking.

## Conditioning on Data

The way to connect our prior with our observations is through `Model.observe`. In its simplest form, it takes two parameters:

* A `Seq[T]` of data
* A `Distribution[T]` which represents the likelihood of that data

Crucially, that likelihood distribution should be parameterized by a random variable. Like this:

```scala mdoc:to-string
val eggModel = Model.observe(eggs, Poisson(lambda))
```

What we get back is something new: a `Model` object. It's printing as `Model[1]`, which means that it's a model of a single parameter, which sounds right.

But what can we do with a `Model`?

## Maximum a Posteriori 

One thing we can do with very little ceremony is to get a single point estimate &emdash; the maximum a posteriori, or MAP &emdash using the L-BFGS optimizer. That just looks like this:

```scala mdoc
eggModel.optimize(lambda)
```

There we have, it folks: the answer to... well, our egg question, anyway.

It might seem a little bit strange that we have to pass in `lambda` here. After all, that's the only parameter our model has. What else could we want to find the MAP for? But Rainier tries to discourage thinking too much about the raw parameterization of a model, and instead to examine whatever random variable represents the quantites you're actually interested in. In this case, that was pretty much the same thing, but it would have been equally valid, for example, to ask for some other MAP instead:

```scala mdoc
eggModel.optimize(lambda * 30)
```

All well and good. But if all we wanted were point estimates, we probably wouldn't be here. What about sampling?

## Finding a Trace

