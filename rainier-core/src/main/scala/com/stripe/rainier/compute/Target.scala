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
                       parameters: List[Parameter])

object TargetGroup {
  def apply(targets: Iterable[Target]): TargetGroup = {
    val (base, batched) = targets.foldLeft((Real.zero, List.empty[Target])) {
      case ((b, l), t) =>
        if (t.nRows == 0)
          (t.real + b, l)
        else
          (b, t :: l)
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
    TargetGroup(base + Real.sum(priors), batched, parameters)
  }
}
