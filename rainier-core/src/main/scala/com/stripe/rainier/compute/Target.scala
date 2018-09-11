package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  def inlined = inl(placeholders)

  private def inl(data: Map[Variable, Array[Double]]): Real =
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

  def batched(nBatches: Int): (Target, Real) =
    if (placeholders.isEmpty)
      (this, Real.zero)
    else if (nBatches == 1)
      (Target.empty, inlined)
    else {
      val n = nRows(placeholders)
      val variables = placeholders.keys.toList
      val nColumns = n / nBatches
      val copies = 0.until(nColumns).map { i =>
        val columnVariables = variables.map { v =>
          v -> new Variable
        }.toMap
        val columnReal = PartialEvaluator.inline(real, columnVariables)
        (i, columnReal, columnVariables)
      }
      val newReal = Real.sum(copies.map(_._2))
      val newPlaceholders = copies.flatMap {
        case (i, _, columnVariables) =>
          columnVariables.toList.map {
            case (v, cv) =>
              val values = placeholders(v)
              cv -> values.slice(i * nBatches, (i + 1) * nBatches)
          }
      }.toMap
      val newTarget = new Target(newReal, newPlaceholders)
      val remainder =
        if (nColumns * nBatches == n)
          Real.zero
        else {
          val data = placeholders.map {
            case (v, d) =>
              v -> d.slice(nColumns * nBatches, n)
          }
          inl(data)
        }
      (newTarget, remainder)
    }
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
