package com.stripe.rainier.compute

import Log._

class Target(val real: Real, tryInline: Boolean) {
  def nRows: Int =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head.values.size

  val placeholders: List[Placeholder] =
    RealOps.variables(real).collect { case v: Placeholder => v }.toList
  val parameters: Set[Parameter] =
    RealOps.variables(real).collect { case v: Parameter => v }

  def maybeInlined: Option[Real] =
    if (nRows == 0)
      Some(real)
    else if (tryInline && Real.inlinable(real)) {
      FINE.log("Inlining %d rows of data", nRows)
      Some(PartialEvaluator.inline(real, nRows))
    } else {
      FINE.log("Did not inline %d rows of data", nRows)
      None
    }

  def batched: (List[Variable], List[Real]) =
    (placeholders, List(real))
}

object Target {
  def apply(real: Real, tryInline: Boolean = true): Target = {
    new Target(real, tryInline)
  }

  val empty: Target = apply(Real.zero)
}

case class TargetGroup(base: Real,
                       batched: List[Target],
                       parameters: List[Parameter])

object TargetGroup {
  def apply(targets: Iterable[Target]): TargetGroup = {
    val (base, batched) = targets.foldLeft((Real.zero, List.empty[Target])) {
      case ((b, l), t) =>
        t.maybeInlined match {
          case Some(r) => ((b + r), l)
          case None    => (b, t :: l)
        }
    }
    val parameters =
      batched
        .foldLeft(RealOps.variables(base).collect { case x: Parameter => x }) {
          case (set, target) =>
            set ++ target.parameters
        }
        .toList
        .sortBy(_.param.sym.id)
    val priors = parameters.map(_.density)

    TargetGroup(base + Real.sum(priors), batched.reverse, parameters)
  }
}
