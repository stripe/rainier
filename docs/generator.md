---
id: generator
title: Generator
---

Assuming `Generator[T]`

### Instance methods

* `repeat(k: Real): Generator[Seq[T]]`

Monad interface:

* `map[U](fn: T => U): Generator[U]`
* `flatMap[U](fn: T => Generator[U]): Generator[U]`
* `zip[U](other: Generator[U]): Generator[(T, U)]`

### Object methods

Construct generators by wrapping some other value using `Generator(t: T): Generator[U]`

This requires a valid `ToGenerator[T,U]` typeclass. In plain terms:

* wrapping `Real` will convert to `Generator[Double]`
* `Distribution[T]` will convert to `Generator[T]`
* `(Generator[A], Generator[B])` will convert to `Generator[(A,B)]`, and similarly for tuples of size 3 or 4
* `Seq[Generator[T]` or `Vec[Generator[T]]` will convert to `Generator[Seq[T]]`
* `Map[K,Generator[V]]` will convert to `Generator[Map[K,V]]`

And, most importantly, this all happens recursively. So, `Map[String,Real]` will, as needed, transform into `Generator[Map[String,Double]]`.
