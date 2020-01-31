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

* `predict[T,U](value: T): List[U]`

TODO