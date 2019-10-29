package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  val nRows: Int =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head._2.size

  val placeholderVariables: List[Variable] = placeholders.keys.toList
  val variables: Set[Variable] = RealOps.variables(real) -- placeholderVariables

  private def inlineRow(i: Int): Real = {
    val row: Map[Variable, Real] = placeholders.map {
      case (v, a) => v -> Real(a(i))
    }
    PartialEvaluator.inline(real, row)
  }

  def inlined: Real =
    if (placeholders.isEmpty)
      real
    else {
      val inlinedRows =
        0.until(nRows).map { i =>
          inlineRow(i)
        }
      Real.sum(inlinedRows.toList)
    }

  def maybeInlined(maxInlineTerms: Int): Option[Real] =
    if (placeholders.isEmpty)
      Some(real)
    else {
      var result = Real.zero
      var i = 0
      while (i < nRows) {
        result += inlineRow(i)
        i += 1
        result match {
          case l: Line if (l.ax.size > maxInlineTerms) =>
            return None
          case _ => ()
        }
      }
      Some(result)
    }

  def batched(batchBits: Int): (List[Variable], List[Real]) = {
    val (batchVariables, outputs) =
      0.to(batchBits)
        .toList
        .map { i =>
          batch(i)
        }
        .unzip

    (placeholderVariables ++ batchVariables.flatMap(_.flatten),
     real :: outputs.reverse)
  }

  private def batch(bit: Int): (List[List[Variable]], Real) = {
    val emptyV = placeholderVariables.map { _ =>
      List.empty[Variable]
    }
    0.until(1 << bit).foldLeft((emptyV, Real.zero)) {
      case ((v, r), _) =>
        val newVars = placeholderVariables.map { _ =>
          new Variable
        }
        val newReal =
          PartialEvaluator.inline(real, placeholderVariables.zip(newVars).toMap)
        val newV = v.zip(newVars).map { case (l, x) => l :+ x }
        (newV, r + newReal)
    }
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

case class TargetGroup(base: Real,
                       batched: List[Target],
                       variables: List[Variable])

object TargetGroup {
  def apply(targets: Iterable[Target], maxInlineTerms: Int): TargetGroup = {
    val (base, batched) = targets.foldLeft((Real.zero, List.empty[Target])) {
      case ((b, l), t) =>
        t.maybeInlined(maxInlineTerms) match {
          case Some(r) => ((b + r), l)
          case None    => (b, t :: l)
        }
    }
    val variables =
      batched
        .foldLeft(RealOps.variables(base)) {
          case (set, target) =>
            set ++ target.variables
        }
        .toList
        .sortBy(_.param.sym.id)
    TargetGroup(base, batched, variables)
  }
}
