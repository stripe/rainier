---
id: vectors
title: Vectors and Variables
---

```scala mdoc:invisible
import com.stripe.rainier.core._
import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

implicit val rng = RNG.default
```

Our imaginary chicken farmer from the [previous section](likelihoods.md) has gotten more sophisticated: she's testing three different types of feed to see which one produces more eggs.

## Counting More Chickens

The egg dataset now includes a `0`, `1`, or `2` for each day that indicates which type of feed they got.

```scala mdoc:pprint
val eggs = List[(Int, Long)]((0,31), (2,47), (0,35), (2,40), (0,33), (2,44), (0,30), (2,46), (0,33), (0,30), (2,36), (2,54), (1,45), (1,39), (2,62), (2,54), (1,30), (2,40), (2,48), (1,33), (0,40), (2,38), (0,31), (2,46), (1,41), (1,42), (0,39), (1,29), (0,28), (1,36), (2,46), (2,33), (2,41), (2,48), (1,32), (0,24), (1,34), (2,48), (1,52), (1,37), (0,28), (0,37), (2,51), (2,44), (1,40), (0,41), (0,36), (1,44), (0,32), (0,31), (0,31), (0,32), (0,33), (1,27), (0,40), (2,45), (2,40), (1,46), (0,35), (2,46), (0,34), (1,41), (0,38), (0,34), (2,46), (1,44), (2,49), (2,39), (1,41), (2,37), (1,29), (0,29), (2,41), (2,46), (1,42), (1,34), (1,32), (1,35), (0,32), (1,40), (1,37), (1,38), (1,42), (1,38), (1,36), (0,38), (0,41), (1,51), (1,40))
```

As before, we'll create a `lambda` that captures the baseline egg-laying rate for the flock.

```scala mdoc:pprint
val lambda = Gamma(0.5, 100).latent
```

This time, however, we'll also create a vector of 3 random variables that represent the egg-laying rate for each of the 3 different feeds. We want these to be able to scale the baseline rate up or down a small amount. There are a lot of different modeling choices we could make here, but in this case we'll start by defining random variables that represent the _log_ of those rates, normally distributed around the log of the baseline, with a small standard deviation.

```scala mdoc:pprint
val logFeeds = Normal(lambda.log, 0.1).latentVec(3)
```

This gives us back a `Vec[Real]`, which is a type defined by Rainier. It's similar to a Scala `Vector[Real]`, but adapted to work better with random variables.

Like a regular `Vector`, we can transform it using `map`. Here, we want to bring these random variables back out of log-space:

```scala mdoc:pprint
val feeds = logFeeds.map(_.exp)
```

Again, a sanity check on the bounds: everything in the `Vec` is now >= 0, so we haven't screwed up our ability to use these as rates.

## Multivariate Data

We'd now like to construct a model that associates these three different (but related) `feed` rates with their corresponding observations. We'll do that in two different ways.

* First, we'll split the data into three parts, and construct three separate models, which we'll then merge.
* Next, we'll use use a variation of `Model.observe` that accepts _vectors_ of likelihoods, one for each observation.

It's worth noting that we can just keep reusing the same `feeds` random variables as we construct each of these models. That's because everything in Rainier is immutable - there's no global context that you're modifying when you create parameters or make observations. That makes things a lot easier to reason about, especially when messing around in a REPL or a notebook.

## Merging Models

For any single type of feed, creating a model looks exactly as it did in the [previous section](likelihoods.md). For example, we could filter to just type 0:

```scala mdoc:pprint
val eggs0 = eggs.filter{case (f, _) => f == 0}.map{case (_, c) => c}
val model0 = Model.observe(eggs0, Poisson(feeds(0)))
```

You'll notice that this is a `Model[2]`, because it has two parameters: the baseline `lambda`, and then the `feeds(0)` which is derived from it.

Similarly, we can do this for all the feed types at once by first grouping the data by feed type, and then mapping over the groups:

```scala mdoc:pprint
val models = eggs.groupBy(_._1).toList.map{
    case (i, data) =>
        val counts = data.map(_._2)
        Model.observe(counts, Poisson(feeds(i)))
}
```

Now we have three different `Model[2]` objects, each of them referencing the same `lambda` but a different element of `feeds`.

Finally, we can use `Model`'s `merge` method to _combine_ all three models into a single joint model for all three feed types.

```scala mdoc:pprint
val mergedModel = models.reduce{(m1, m2) => m1.merge(m2)}
```

This model has 4 parameters, as it should.

There's nothing wrong with building the model this way in this situation, where you have a categorical independent variable. However, if you had a continuous covariate, this wouldn't work (or rather, it would _work_, but it would require building a separate `Model` for each data point, which scales very badly), so it's good to see an alternative way of solving the same problem.

## Vector Likelihoods

So far we've used `Model.observe` with the following signature: `observe[Y](ys: Seq[Y], likelihood: Distribution[Y])`. That is: a sequence of observations, and a single distribution that provides the likelihood function for all of them.

Another option is `observe[Y](ys: Seq[Y], likelihoods: Vec[Distribution[Y])`. In this case, rather than assuming that each `Y` has an identical likelihood distribution, we pass in a whole `Vec` of `Distribution` objects, one for each value in `ys`.

To make use of it, we first have to separate our data into our independent variables and dependent variable.

```scala mdoc:pprint
val eggFeeds = eggs.map(_._1)
val eggCounts = eggs.map(_._2)
```

Next, we want to create a `Vec` from our independent variables (that is, `eggFeeds`), and map over them to create a `Poisson` for each one. The code is quite straightforward.

```scala mdoc:pprint
val feedsVec = Vec.from(eggFeeds)
val poissonVec = feedsVec.map{i: Real => Poisson(feeds(i))}
```

What happens here, though, is a little bit strange: when we pass it to `Vec.from`, our regular integer data gets transformed into a `Vec[Real]`. In fact, _any_ combination of numbers, tuples, lists, and maps will get converted into its `Real`-based equivalent: so for example a `Seq[Map[String,Double]]` will become a `Vec[Map[String,Real]]`. Anything that can't be converted like this cannot be used to construct a `Vec`.

For a clue as to why, check out the next line: the `i` that we're using to index `feeds` is, of course, a `Real`. It represents *some* row from `eggFeeds`, but we're not given any insight into *which* row it is, or any ability to act differently for some rows than others; and similarly, by indexing into `feeds` with a `Real`, we know we are receiving _some_ element of `feeds`, but not _which_ element. All of that helps with vectorizing the computation and helping things scale nicely.

If you didn't entirely follow that last bit, that's ok. The short version is that as long as you can transform your data into primitive data structures like lists and maps of numbers, and then stuff them into a `Vec`, Rainier will be happy.

Finally, we'll build the model itself.

```scala mdoc:pprint
val vecModel = Model.observe(eggCounts, poissonVec)
```

Hooray! We're back to a `Model[4]` that is, mathematically, the same as the `mergedModel` we produced before (but using a much more general technique).