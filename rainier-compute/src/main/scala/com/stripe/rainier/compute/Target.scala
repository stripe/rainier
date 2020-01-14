package com.stripe.rainier.compute

class Target(val real: Real) {
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
    else
      None

  private[compute] def batched: (List[Column], List[Real]) =
    (columns, List(real))
}

object Target {
  def apply(real: Real): Target = {
    new Target(real)
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
