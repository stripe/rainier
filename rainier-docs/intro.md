# Introduction to Rainier

## Who Rainier is for

Rainier is for building and sampling from Bayesian statistical models. Specifically, it's for building generative models with fixed structure, continuous parameters, and data that can comfortably fit in memory. Very often, that means some kind of generalized linear mixed model (GLMM).

This documentation assumes you have at least some basic familiarity with Bayesian modeling and with GLMMs. If you don't, the single best text on that subject is McElreath's [Statistical Rethinking](https://xcelab.net/rm/statistical-rethinking/), and we highly recommend reading it.

We also assume you are familiar with Scala. Perhaps this goes without saying, but: Rainier is a Scala library, and letting you build and run your models in Scala, on the JVM, is one of Rainier's distinguishing features.

## Rainier basics

```scala mdoc:silent
import com.stripe.rainier.core._
```

The most fundamental data type in Rainier is the `Real`, which represents a single real-valued random variable. A single real value is simple enough: that's like a `Double`, and indeed you can treat a `Real` just like a `Double` in a lot of ways. But since it's a [random variable](https://en.wikipedia.org/wiki/Random_variable), we don't in general know *which* specific real value it represents.

To construct a `Real`, we very often start with a `Distribution` object. For example, here we first construct a `Uniform(0,1)` distribution, and then use `param` to create a new random variable, `a`, with that distribution as its prior.

```scala mdoc:to-string
val a = Uniform(0,1).param
val b = a + 1
```

Although we don't know the exact value, you can see in the output that Rainier is tracking the bounds of each `Real`, as best it can: we know that `a` must be in the range `(0,1)`, which means `b` must be within `(1,2)`. Seeing these bounds can be a good basic sanity check as you're building a model.



