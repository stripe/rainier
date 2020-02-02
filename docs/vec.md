---
id: vec
title: Vec
---

`Vec[T]` is found in `com.stripe.rainier.compute`.

## Instance methods

Indexing:

* `apply(index: Int): T`
* `apply(index: Real): T`

Applicative:

* `map[U](fn: T => U): Vec[U]`
* `zip[U](other: Vec[U]): Vec[(T, U)]`

Collections: `size`, `toList`, `take`, `drop`

Only for `Vec[Real]`:

* `dot(other: Vec[Real]): Real`

## Object methods

* `from[T](seq: Seq[T]): Vec[U]`

This will construct a new `Vec` that is the `Real`-space transformation of any simple data structure `T`. That is:
* any numbers will be converted to `Real`
* tuples, Maps, and Lists will recursively convert their elements

Under the hood, this is done by an implicitly-constructed `ToVec[T,U]` typeclass.

* `Vec(t, ...)` is the var-args version of `from`