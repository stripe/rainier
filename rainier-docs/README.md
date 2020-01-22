# Rainier mdoc Example

## mdocVariables!

To install Rainier include the following in your `build.sbt`
```scala
libraryDependencies += "com.stripe" % "rainier-core" % "@VERSION@"
```

## Imports!

```scala mdoc:silent
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
```

## Silent blocks!

```scala mdoc:silent
implicit val rng: RNG = RNG.default

val x = Normal.standard.param
val y = Uniform(0, x).param
```

## Regular output!

```scala mdoc
val sampler: Sampler = HMC(5, 1000, 1000)
```

## Evilplot!

```scala mdoc:evilplot:assets/scatterplot.png
import com.stripe.rainier.core._
import com.stripe.rainier.plot.Jupyter._

val modelTrace = Model(x, y).sample(sampler)

val p = scatter(modelTrace.predict((x,y)))
show("x", "y", p)
```
