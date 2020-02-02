---
id: vectors
title: Vectors and Variables
---

```scala mdoc:invisible
import com.stripe.rainier.core._
import com.stripe.rainier.compute._
```

Our imaginary chicken farmer from the [previous section](likelihoods.md) has gotten more sophisticated: she's testing three different types of feed to see which one produces more eggs.

## Counting More Chickens

The egg dataset now includes a `0`, `1`, or `2` for each day that indicates which type of feed they got.

```scala mdoc:silent
val eggs = List[(Int, Long)]((0,31), (2,47), (0,35), (2,40), (0,33), (2,44), (0,30), (2,46), (0,33), (0,30), (2,36), (2,54), (1,45), (1,39), (2,62), (2,54), (1,30), (2,40), (2,48), (1,33), (0,40), (2,38), (0,31), (2,46), (1,41), (1,42), (0,39), (1,29), (0,28), (1,36), (2,46), (2,33), (2,41), (2,48), (1,32), (0,24), (1,34), (2,48), (1,52), (1,37), (0,28), (0,37), (2,51), (2,44), (1,40), (0,41), (0,36), (1,44), (0,32), (0,31), (0,31), (0,32), (0,33), (1,27), (0,40), (2,45), (2,40), (1,46), (0,35), (2,46), (0,34), (1,41), (0,38), (0,34), (2,46), (1,44), (2,49), (2,39), (1,41), (2,37), (1,29), (0,29), (2,41), (2,46), (1,42), (1,34), (1,32), (1,35), (0,32), (1,40), (1,37), (1,38), (1,42), (1,38), (1,36), (0,38), (0,41), (1,51), (1,40))
```

As before, we'll create a `lambda` that captures the baseline egg-laying rate for the flock.

```scala mdoc:to-string
val lambda = Gamma(0.5, 100).real
```

This time, however, we'll also create a vector of 3 random variables that represent the egg-laying rate for each of the 3 different feeds. We want these to be able to scale the baseline rate up or down a small amount. There are a lot of different modeling choices we could make here, but in this case we'll start by defining random variables that represent the _log_ of those rates, normally distributed around the log of the baseline, with a small standard deviation.

```scala mdoc:to-string
val logFeeds = Normal(lambda.log, 0.1).vec(3)
```

This gives us back a `Vec[Real]`, which is a type defined by Rainier. It's similar to a Scala `Vector[Real]`, but adapted to work better with random variables.

Like a regular `Vector`, we can transform it using `map`. Here, we want to bring these random variables back out of log-space:

```scala mdoc:to-string
val feeds = logFeeds.map(_.exp)
```

Again, a sanity check on the bounds: everything in the `Vec` is now >= 0, so we haven't screwed up our ability to use these as rates.

## Multivariate Data

We'd now like to construct a model that associates these three different (but related) `feed` rates with their corresponding observations. We'll do that in three different ways.

* First, we'll split the data into three parts, and construct three separate models, which we'll then merge.
* Next, we'll use a variation of `Model.observe` that lets us define a function between our independent variables (feed type), and the likelihood of our dependent variable (number of eggs).
* Finally, we'll see how to construct that function in a special way that lets Rainier evaluate it much more efficiently.

It's worth noting that we can just keep reusing the same `feeds` random variables as we construct each of these models. That's because everything in Rainier is immutable - there's no global context that you're modifying when you create parameters or make observations. That makes things a lot easier to reason about, especially when messing around in a REPL or a notebook.

## Merging Models

For any single type of feed, creating a model looks exactly as it did in the [previous section](likelihoods.md). For example, we could filter to just type 0:

```scala mdoc:to-string
val eggs0 = eggs.filter{case (f, _) => f == 0}.map{case (_, c) => c}
val model0 = Model.observe(eggs0, Poisson(feeds(0)))
```

You'll notice that this is a `Model[2]`, because it has two parameters: the baseline `lambda`, and then the `feeds(0)` which is derived from it.

Similarly, we can do this for all the feed types at once by first grouping the data by feed type, and then mapping over the groups:

```scala mdoc:to-string
val models = eggs.groupBy(_._1).toList.map{
    case (i, data) =>
        val counts = data.map(_._2)
        Model.observe(counts, Poisson(feeds(i)))
}
```

Now we have three different `Model[2]` objects, each of them referencing the same `lambda` but a different element of `feeds`.

Finally, we can use `Model`'s `merge` method to _combine_ all three models into a single joint model for all three feed types.

```scala mdoc:to-string
val mergedModel = models.reduce{(m1, m2) => m1.merge(m2)}
```

This model has 4 parameters, as it should.

There's nothing wrong with building the model this way in this situation, where you have a categorical independent variable. However, if you had a continuous covariate, this wouldn't work, so it's good to see an alternative way of solving the same problem.

## Mapping Variables

So far we've used `Model.observe` with the following signature: `observe[Y](ys: Seq[Y], likelihood: Distribution[Y])`. That is: a single vector of observations, and a single distribution that provides its likelihood function.

Another option is `observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y])`. In this case, we assume we can structure our data as `(X,Y)` pairs, and describe a function of `X` that will produce a new `Distribution[Y]` for each observation.

To make use of it, we first have to split our data into separate `xs` and `ys` vectors, for our independent variables and dependent variable, respectively.

```scala mdoc:to-string
val eggFeeds = eggs.map(_._1)
val eggCounts = eggs.map(_._2)

val mappedModel =
    Model.observe(eggFeeds, eggCounts){feed: Int =>
        Poisson(feeds(feed))
    }
```

What we end up with is a `Model[4]` that is, mathematically, the same as the `mergedModel` we produced before. In fact, this approach is identical to first producing a separate model for each observation, and then merging all of them together. So we could, equivalently, have done something like this:

```scala
eggs
    .map{case (feed, count) => Model.observe(List(count), Poisson(feeds(feed)))}
    .reduce((m1, m2) => m1.merge(m2))
```

Unfortunately, that also highlights a problem with this approach: by creating so many individual tiny models, you open yourself up to performance problems. It's not an issue with small scale data like we're working with here, but if you had tens or hundreds of thousands of observations, you would definitely notice it. Luckily, with a little bit more work, there's a way around that.

## Encoding Variables

To get ahead of this potential performance problem, we need to introduce a new type, the `Fn`. Just like `Vec` is a specialized `Vector`, `Fn` is a specialized Scala function.

Let's use it first, and explain afterwards:

```scala mdoc:to-string
val encoder = Fn.int
val noise = encoder.map{feed: Real => Poisson(feeds(feed))}
val encodedModel = Model.observe(eggFeeds, eggCounts, noise)
```

You can see that this ends up not especially different from the `mappedModel` code above. However, it will perform much better, especially with larger amounts of data.

So what's going on?

TODO