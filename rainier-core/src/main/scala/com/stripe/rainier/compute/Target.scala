package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  val nRows: Int =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head._2.size

  val placeholderVariables: List[Variable] = placeholders.keys.toList
  val parameters: Set[Parameter] =
    RealOps.variables(real).collect { case v: Parameter => v }

  def maybeInlined: Option[Real] =
    if (nRows == 0)
      Some(real)
    else if (Real.inlinable(real))
      Some(PartialEvaluator.inline(real, nRows))
    else
      None

  def batched: (List[Variable], List[Real]) =
    (placeholderVariables, List(real))
}

object Target {
  def apply(real: Real): Target = {
    val placeholders =
      RealOps.variables(real).collect { case v: Placeholder => v }
    new Target(real, placeholders.map { p =>
      p -> p.values
    }.toMap)
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
