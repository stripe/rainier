package com.stripe.rainier.compute

class Target(val real: Real) {
  val (placeholders, parameters) = {
    val variables = RealOps.variables(real)
    (variables.collect{case x:Placeholder => x}.toList,
    variables.collect{case x:Parameter => x})
  }

  val nRows: Int =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head.values.size
}

object Target {
  val empty: Target = new Target(Real.zero)
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
