package com.stripe.rainier.compute

import Log._

class Target(val real: Real, tryInline: Boolean) {
  def nRows: Int =
    if (columns.isEmpty)
      0
    else
      columns.head.values.size

  val columns: List[Column] =
    RealOps.columns(real).toList

  val parameters: Set[Parameter] =
    RealOps.parameters(real)

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

  def batched: (List[Column], List[Real]) =
    (columns, List(real))
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
        .foldLeft(RealOps.parameters(base)) {
          case (set, target) =>
            set ++ target.parameters
        }
        .toList
        .sortBy(_.param.sym.id)
    val priors = parameters.map(_.density)

    TargetGroup(base + Real.sum(priors), batched.reverse, parameters)
  }
}
