# Rainier mdoc Example

## mdocVariables!

To install Rainier include the following in your `build.sbt`
```scala
libraryDependencies += "com.stripe" % "rainier-core" % "0.3.0-SNAPSHOT"
```

## Imports!

```scala
import com.stripe.rainier.core._
import com.stripe.rainier.sampler._
```

## Silent blocks!

```scala
implicit val rng: RNG = RNG.default

val x = Normal.standard.param
val y = Uniform(0, x).param
```

## Regular output!

```scala
val sampler: Sampler = HMC(5, 1000, 1000)
// sampler: Sampler = HMC(5, 1000, 1000)
```

## Evilplot!

![](assets/scatterplot.png)