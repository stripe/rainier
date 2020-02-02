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

Construct generators by wrapping some `t: T` value using `Generator(t)`.

This will produce a `Generator[U]` and requires a valid `ToGenerator[T,U]` typeclass. In plain terms:

* `t: Real` will produce `Generator[Double]`
* `t: Distribution[A]` will produce `Generator[A]`
* `t: (Generator[A], Generator[B])` will produce `Generator[(A,B)]`, and similarly for tuples of size 3 or 4
* `t: Seq[Generator[A]` or `t: Vec[Generator[A]]` will produce `Generator[Seq[A]]`
* `t: Map[K,Generator[V]]` will produce `Generator[Map[K,V]]`

And, most importantly, this all happens recursively. So, `t: Map[String,Real]` will, produce `Generator[Map[String,Double]]`.
