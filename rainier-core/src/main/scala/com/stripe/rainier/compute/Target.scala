package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  val nRows = size(placeholders)
  val placeholderVariables = placeholders.keys.toList

  def inlined = inl(placeholders)

  def batched(nBatches: Int): (Target, Real) =
    if (placeholders.isEmpty)
      (this, Real.zero)
    else if (nBatches == 1)
      (Target.empty, inlined)
    else {
      val nCopies = nRows / nBatches
      val (realCopies, variableCopies) = makeCopies(nCopies)
      val newReal = Real.sum(realCopies)
      val newPlaceholders = makePlaceholders(variableCopies, nBatches).toMap
      val newTarget = new Target(newReal, newPlaceholders)
      val remainder = makeRemainder(nBatches, nCopies)
      (newTarget, remainder)
    }

  private def makeCopies(
      nCopies: Int): (Seq[Real], Seq[Map[Variable, Variable]]) =
    0.until(nCopies)
      .map { _ =>
        val variablesCopy = placeholderVariables.map { v =>
          v -> new Variable
        }.toMap
        val realCopy = PartialEvaluator.inline(real, variablesCopy)
        (realCopy, variablesCopy)
      }
      .unzip

  private def makePlaceholders(variableCopies: Seq[Map[Variable, Variable]],
                               nBatches: Int): Seq[(Variable, Array[Double])] =
    for {
      (copyVariables, i) <- variableCopies.zipWithIndex
      (v, cv) <- copyVariables
    } yield {
      cv -> placeholders(v).slice(i * nBatches, (i + 1) * nBatches)
    }

  private def makeRemainder(nBatches: Int, nCopies: Int): Real =
    if (nCopies * nBatches == nRows)
      Real.zero
    else {
      val data = placeholders.map {
        case (v, d) =>
          v -> d.slice(nCopies * nBatches, nRows)
      }
      inl(data)
    }

  private def inl(data: Map[Variable, Array[Double]]): Real =
    if (data.isEmpty)
      real
    else {
      val inlined = 0.until(size(data)).map { i =>
        val row: Map[Variable, Real] = data.map {
          case (v, a) => v -> Real(a(i))
        }
        PartialEvaluator.inline(real, row)
      }
      Real.sum(inlined.toList)
    }

  private def size(data: Map[Variable, Array[Double]]): Int =
    if (data.isEmpty)
      0
    else
      data.head._2.size
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
