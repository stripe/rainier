---
id: vectors
title: Vectors and Variables
---

```scala mdoc:invisible
import com.stripe.rainier.core._
import com.stripe.rainier.compute._
import com.stripe.rainier.plot._
import Jupyter._
```

Our imaginary chicken farmer from the [previous section](likelihoods.md) has gotten more sophisticated: she's testing three different types of feed to see which one produces more eggs.

## Counting your Chickens, Redux

The egg dataset now includes a `0`, `1`, or `2` for each day that indicates which type of feed they got.

```scala mdoc:silent
val eggs = List[(Int, Long)]((0, 45), (1, 52), (2, 45))
```

As before, we'll create a `lambda` that captures the baseline egg-laying rate for the flock.

```scala mdoc:to-string
val lambda = Gamma(0.5, 100).param
```

This time, however, we'll also create a vector of 3 random variables that represent the egg-laying rate for each of the 3 different feeds. We want these to be able to scale the baseline rate up or down a small amount. There are a lot of different modeling choices we could make here, but in this case we'll start by defining random variables that represent the _log_ of those rates, normally distributed around the log of the baseline, with a small standard deviation.

```scala mdoc:to-string
val logFeeds = Normal(lambda.log, 0.1).paramVector(3)
```

This gives us back a `Vec[Real]`, which is a type defined by Rainier. It's similar to a Scala `Vector[Real]`, but adapted to work well with `Real`. We'll see more about that later.

Like a regular `Vector`, we can transform it using `map`: here, to bring these random variables back out of log-space.

```scala mdoc:to-string
val feeds = logFeed.map(_.exp)
```

Again, a sanity check on the bounds: everything in the `Vec` is now >= 0, so we haven't screwed up our ability to use these as rates.

## Multivariate Data

We'd now like to construct a model that associates these three different (but related) `feed` rates with their corresponding observations. We'll do that in three different ways.

* First, we'll split the data into three parts, and construct three separate models, which we'll then merge.
* Next, we'll use a variation of `Model.observe` that lets us define a function between our independent variables (feed type), and the likelihood of our dependent variable (number of eggs).
* Finally, we'll see how to construct that function in a special way that lets Rainier evaluate it much more efficiently.

It's worth noting that we can just keep reusing the same `feeds` random variables as we construct each of these models. That's because everything in Rainier is immutable - there's no global context that you're modifying when you create parameters or make observations. That makes things a lot easier to reason about, especially when messing around in a REPL or a notebook.



