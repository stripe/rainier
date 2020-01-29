---
id: vectors
title: Vectors and Covariates
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

This time, however....

```scala mdoc:to-string
val feeds = Normal(lambda.log, 0.1).paramVector(3).map(_.exp)
```

```
- logLambda ~ Normal
- feed ~ Normal(logLambda, 0.1).paramVec(3)
- three ways to do it:
   - separate model for each and merge them
   - Model.observe with regular function
   - Model.observe with Fn
- note that models are immutable
```