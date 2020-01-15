package com.stripe.rainier.compute

class Target(val real: Real, val gradient: List[Real]) {
  val columns: List[Column] =
    (real :: gradient).toSet.flatMap { r =>
      RealOps.columns(r)
    }.toList
}

case class TargetGroup(targets: List[Target], parameters: List[Parameter])

object TargetGroup {
  def apply(reals: List[Real]): TargetGroup = {
    val parameters =
      reals.toSet
        .flatMap(RealOps.parameters)
        .toList
        .sortBy(_.param.sym.id)

    val priors = Real.sum(parameters.map(_.density))
    val targets = (priors :: reals).map { r =>
      val grads = Gradient.derive(parameters, r)
      new Target(r, grads)
    }

    TargetGroup(targets, parameters)
  }
}
