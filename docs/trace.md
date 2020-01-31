---
id: trace
title: Trace and Generator
---

`Trace` and `Generator` are both found in `com.stripe.rainier.core`.

## Trace

* `diagnostics: List[Trace.Diagnostics]`

Produce a list of `Diagnostics(rHat: Double, effectiveSampleSize: Double)`, one for each parameter. Requires chains > 1.

* `thin(n: Int): Trace`

Keep every n'th sample in each chain.

* `predict[T](generator: Generator[T]): List[T]`

Generate one value from `generator` from each sample in the trace. Supports automatic conversion from `Real` and other structures via `ToGenerator` as explained below.

## ToGenerator

More or less anywhere you can use a `Generator[T]`, you can also use some value of type `S` for which Rainier can construct a `ToGenerator[S,T]` typeclass.

In plain terms, this means that:

* `Real` will automatically convert to `Generator[Double]`
* `Distribution[T]` will convert to `Generator[T]`
* `(Generator[A], Generator[B])` will convert to `Generator[(A,B)]`, and similarly for tuples of size 3 or 4
* `Seq[Generator[T]` or `Vec[Generator[T]]` will convert to `Generator[Seq[T]]`
* `Map[K,Generator[V]]` will convert to `Generator[Map[K,V]]`

And, most importantly, this all happens recursively. So, `Map[String,Real]` will, as needed, transform into `Generator[Map[String,Double]]`.

## Generator

Assuming `Generator[T]`

### Instance methods

* `repeat(k: Real): Generator[Seq[T]]`

Monad interface:

* `map[U](fn: T => U): Generator[U]`
* `flatMap[U](fn: T => Generator[U]): Generator[U]`
* `zip[U](other: Generator[U]): Generator[(T, U)]`

### Object methods

* `constant[T](t: T): Generator[T]`
* `real(x: Real): Generator[Double]`
* `vector[T](v: Vector[T]): Generator[T]`
* `traverse[T](seq: Seq[Generator[T]]): Generator[Seq[T]]`
* `traverse[K, V](m: Map[K, Generator[V]]: Generator[Map[K, V]]`
