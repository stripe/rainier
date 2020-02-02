---
id: real
title: Real
---

`Real` is found in `com.stripe.rainier.compute`.

## Instance methods

Arithmetic: `+`, `-`, `*`, `/`, `pow(exponent: Real)`

Unary: `-`, `abs`, `exp`, `log`, `logit`, `logistic`

Comparison: `min`, `max`

Trigonometry: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `sinh`, `cosh`, `tanh`

## Object methods

Construct with `Real(a)` for any numeric value `a`.

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
