package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  val nRows =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head._2.size

  val placeholderVariables: List[Variable] = placeholders.keys.toList
  val variables: Set[Variable] = RealOps.variables(real) -- placeholderVariables

  def inlined: Real =
    if (placeholders.isEmpty)
      real
    else {
      val inlinedRows = 0.until(nRows).map { i =>
        val row: Map[Variable, Real] = placeholders.map {
          case (v, a) => v -> Real(a(i))
        }
        PartialEvaluator.inline(real, row)
      }
      Real.sum(inlinedRows.toList)
    }

  def updater: (Real, List[Variable]) = {
    val newVars = placeholderVariables.map { _ =>
      new Variable
    }
    val newReal =
      PartialEvaluator.inline(real, placeholderVariables.zip(newVars).toMap)
    (newReal, newVars)
  }
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
