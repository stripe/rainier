package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: List[Placeholder]) {
  val nRows: Int =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head.values.size

  val parameters: Set[Parameter] = RealOps.parameters(real)
}

object Target {
  def apply(real: Real): Target = new Target(real, Nil)
  val empty: Target = apply(Real.zero)
}

case class TargetGroup(base: Real,
                       batched: List[Target],
                       variables: List[Variable])

object TargetGroup {
  def apply(targets: Iterable[Target]): TargetGroup = {
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
    val priors = variables.collect { case a: Parameter => a.density }

    TargetGroup(base + Real.sum(priors), batched, variables)
  }
}
