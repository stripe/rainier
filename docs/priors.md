---
id: priors
title: Random Variables and Priors
---

```scala mdoc:silent
import com.stripe.rainier.core._
```

The most fundamental data type in Rainier is the `Real`, which represents a real-valued scalar random variable. A real-valued scalar is simple enough: that sounds like a `Double`, and indeed you can treat a `Real` just like a `Double` in a lot of ways. But since it's a [random variable](https://en.wikipedia.org/wiki/Random_variable), we don't in general know *which* specific real value it represents. (That's what we're building the model to find out!)

To construct a `Real`, we very often start with a `Distribution` object. For example, here we first construct a `Uniform(0,1)` distribution, and then use `param` to create a new random variable, `a`, with that distribution as its prior.

```scala mdoc:to-string
val a = Uniform(0,1).param
val b = a + 1
```

Although we don't know the exact value, you can see in the output that Rainier is tracking the bounds of each `Real`, as best it can: we know that `a` must be in the range `(0,1)`, which means `b` must be within `(1,2)`. Seeing these bounds can be a good basic sanity check as you're building a model.

You can combine `Real`s using normal arithmetic operations, and they support a wide range of unary operators like `abs`, `exp`, `log`, and `logit`. You can also use a `Real` for any parameter of a `Distribution`. So, for example, we can use our `b` and `a` as the mean and standard deviation of a new `Normal` random variable.

```scala mdoc:to-string
val c = Normal(b, a).param
```

## Sampling from the Prior

The bounds for `c` are not that helpful: *in theory*, it can take on any real value. Some values, however, are much more likely than others. To get a view into that, we can sample from `c`. In fact, we can jointly sample from `a` and `c` to get a sense of not just what values are more and less likely for them, but how those values are related to each other.

We'll see a more thorough treatment of sampling later on, but for now, we can use a simple convenience method that's perfect for this kind of exploratory work.

```scala mdoc
Model.sample((a,c)).take(10)
```

You can see a few things here: the sampling process has converted our `(a, c)` tuple into a list of concrete `(Double, Double)` tuples. Each of those tuples is an independent, but internally consistent, sample of the `(Real, Real)` 2-dimensional random variable. We can verify that the values for `a` are all in that `(0,1)` range we expected, and that the values for `c` are a little more widely spread. But to get a better understanding, we should look at a plot.

## Visualizing Samples

Rainier comes with plotting support, based on [EvilPlot](https://cibotech.github.io/evilplot/), in a separate `rainier-plot` module. If you were in Jupyter, for example, you should do this as well as (or instead of) importing `rainier-core`:

```scala
import $ivy.`com.stripe:rainier-plot:@VERSION@`
```

Followed by importing the package, and the `Jupyter` object where all the plotting methods live:

```scala mdoc
import com.stripe.rainier.plot._
import Jupyter._
```

Finally, we can create and render a basic scatter plot of our `(a,c)` tuples.

```scala
val ac = Model.sample((a,c)))
show("a", "c", scatter(ac))
```

```scala mdoc:evilplot:assets/ac.png
show("a", "c", scatter(Model.sample((a,c))))
```

Here, the relationship is a lot clearer! Because we used `a` as the standard deviation for `c`, we get a funnel shape where `c` spreads out more as `a` gets larger. But we also see that the center of the funnel shifts upwards as `a` grows, because we set the mean to be `b = a + 1`.
