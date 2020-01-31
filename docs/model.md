---
id: model
title: Model and Fn
---

`Model` is found in `com.stripe.rainier.core`, and `Fn` in `com.stripe.rainier.compute`.

## Model

### Instance Methods

* `prior: Model`

Strips away the observations, for ease of checking prior predictions.

* `merge(other: Model): Model`

Combines two models.

* `sample(sampler: Sampler, nChains: Int = 4): Trace`

Run inference using the provided sampler.

* `optimize[T, U](value: T): U`

Run L-BFGS. `value: T` must be something that can be converted to a `Generator[U]`. See the [Trace and Generator](trace.md) documentation for more.

### Object Methods

* `observe[Y](ys: Seq[Y], likelihood: Distribution[Y]): Model`
* `observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model`
* `observe[X, Y](xs: Seq[X], ys: Seq[Y], fn: Fn[X, Distribution[Y]]): Model`

## Fn

Assuming `Fn[A,B]`

### Instance Methods

* `apply(a: A): B`

Applicative:

* `map[C](g: B => C): Fn[A, C]`
* `zip[X,Y](fn: Fn[X,Y]): Fn[(A,X),(B,Y)]`
* `contramap[T](g: T => A): Fn[T,B]`

Repetition:

* `list(k: Int): Fn[Seq[A], List[B]]`
* `keys[K](seq: Seq[K]): Fn[Map[K,A], Map[K,B]]`

Only for `Fn[A,Real]`:

* `vec(k: Int): Fn[Seq[A], Vec[Real]]`

### Object Methods

* `double: Fn[Double, Real]`
* `int: Fn[Int, Real]`
* `long: Fn[Long, Real]`
* `enum[T](choices: List[T]): Fn[T, List[(T, Real)]]`

