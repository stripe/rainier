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

```scala
val sampler: Sampler = HMC(1)
// sampler: Sampler = HMC(1)
val params: Array[Double] = Array.fill(model.parameters.size) { rng.standardUniform }
// params: Array[Double] = Array(
//   0.3053029841476743,
//   0.8523515573096254,
//   0.5423364854407695,
//   0.661504795283736,
//   0.46011343638642643,
//   0.8659819201847979,
//   0.16561455131811054,
//   0.5467571461828816,
//   0.41383155417211104,
//   0.45393199372779414
// )
model.sample(sampler, 1, 2)
// res0: Sample = Sample(
//   List(
//     List(
//       Array(
//         0.21727658476535638,
//         0.17913256471106712,
//         0.8830410455452341,
//         -1.1337632613072355,
//         1.2702381190510892,
//         -0.19716341741258325,
//         -0.22622389045696156,
//         -0.5392177181673204,
//         0.21550576600628651,
//         -0.8613894101108281
//       ),
//       Array(
//         0.21727658476535638,
//         0.17913256471106712,
//         0.8830410455452341,
//         -1.1337632613072355,
//         1.2702381190510892,
//         -0.19716341741258325,
//         -0.22622389045696156,
//         -0.5392177181673204,
//         0.21550576600628651,
//         -0.8613894101108281
//       )
//     )
//   ),
//   Model(
//     List(
//       com.stripe.rainier.compute.Line@297bb366,
//       com.stripe.rainier.compute.Line@4e92dbd0,
//       com.stripe.rainier.compute.Line@641a3fd4,
//       com.stripe.rainier.compute.Line@13d1b607,
//       com.stripe.rainier.compute.Line@67c5d798,
//       com.stripe.rainier.compute.Line@3fd1dceb,
//       com.stripe.rainier.compute.Line@a80b05a,
//       com.stripe.rainier.compute.Line@147f5e83,
//       Scalar(0.0)
//     )
//   )
// )
```
