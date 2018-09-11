package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  def inlined = inl(placeholders)

  def inl(data: Map[Variable, Array[Double]]): Real =
    if (data.isEmpty)
      real
    else {
      val inlined = 0.until(nRows(data)).map { i =>
        val row: Map[Variable, Real] = data.map {
          case (v, a) => v -> Real(a(i))
        }
        PartialEvaluator.inline(real, row)
      }
      Real.sum(inlined.toList)
    }

  private def nRows(data: Map[Variable, Array[Double]]): Int =
    data.head._2.size
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
