---
id: real
title: Real and Vec
---

Both `Real` and `Vec[T]` are found in `com.stripe.rainier.compute`.

## Real

### Instance methods

Arithmetic: `+`, `-`, `*`, `/`, `pow(exponent: Real)`

Unary: `-`, `abs`, `exp`, `log`, `logit`, `logistic`

Comparison: `min`, `max`

Trigonometry: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `sinh`, `cosh`, `tanh`

### Object methods

Constants: `zero`, `one`, `two`, `negOne`, `Pi`, `infinity`, `negInfinity`

Control flow:

* `eq(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real`
* `lt(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real`
* `gt(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real`
* `lte(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real`
* `gte(left: Real, right: Real, ifTrue: Real, ifFalse: Real): Real`

Summation:

* `sum(seq: Iterable[Real]): Real`
* `logSumExp(seq: Iterable[Real]): Real`

## Vec[T]

### Instance methods

Indexing:

* `apply(index: Int): T`
* `apply(index: Real): T`

Applicative:

* `map[U](fn: T => U): Vec[U]`
* `zip[U](other: Vec[U]): Vec[(T, U)]`

Only for `Vec[Real]`:

* `++(other: Vec[Real]): Vec[Real]`
* `dot(other: Vec[Real]): Real`
