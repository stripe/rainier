package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {

  def batched(nBatches: Int): (Target, Real) =
    if (nBatches == 1)
      (Target.empty, inlined)
    else {
      ???
    }

  def inlined = inline(placeholders)

  def inline(data: Map[Variable, Array[Double]]): Real =
    if (data.isEmpty)
      real
    else {
      val nRows = data.head._2.size
      val inlined = 0.until(nRows).map { i =>
        val row: Map[Variable, Real] = data.map {
          case (v, a) => v -> Real(a(i))
        }
        PartialEvaluator.inline(real, row)
      }
      Real.sum(inlined.toList)
    }
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
