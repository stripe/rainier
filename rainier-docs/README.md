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

val ys: List[Int] = List(28, 8, -3, 7, -1, 1, 18, 12)
val sigmas: List[Int] = List(15, 10, 16, 11, 9, 11, 10, 18)

def model: Model = {
  val mu = Normal(0, 5).param
  val tau = Cauchy(0, 5).param.abs
  val thetas = 0.until(sigmas.size).map { _ =>
    Normal(mu, tau).param
  }

  thetas.zip(ys.zip(sigmas)).foldLeft(Model.empty) {
    case (m, (theta, (y, sigma))) =>
      Model
        .observe(y.toDouble, Normal(theta, sigma))
        .merge(m)
  }
}
```

## Lots O' Output!

```scala mdoc
val sampler: Sampler = HMC(1)
val params: Array[Double] = Array.fill(model.parameters.size) { rng.standardUniform }
model.sample(sampler, 1, 2)
```

## Evilplot?

```scala mdoc:evilplot:assets/scatterplot.png
import com.cibo.evilplot._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.plot.aesthetics.DefaultTheme._
import com.cibo.evilplot.numeric.Point

val data = Seq.tabulate(90) { i =>
  val degree = i * 8
  val radian = math.toRadians(degree)
  Point(i.toDouble, math.sin(radian))
}

ScatterPlot(data)
  .xAxis()
  .yAxis()
  .frame()
  .xLabel("x")
  .yLabel("y")
  .render()
```
