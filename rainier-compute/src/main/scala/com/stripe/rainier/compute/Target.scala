package com.stripe.rainier.compute

class Target(val real: Real, val gradient: List[Real]) {
  val columns: List[Column] =
    (real :: gradient).toSet.flatMap { r =>
      TargetGroup.findColumns(r)
    }.toList
}

class TargetGroup(targets: List[Target], val parameters: List[Parameter]) {
  val data =
    targets.map { target =>
      target.columns.map { v =>
        v.values.map(_.toDouble).toArray
      }.toArray
    }.toArray

  val columns = targets.flatMap(_.columns)
  val inputs = parameters.map(_.param) ++ columns.map(_.param)
  val outputs = targets.zipWithIndex.flatMap {
    case (t, i) =>
      val name = s"target_$i"
      (name -> t.real) ::
        t.gradient.zipWithIndex.map {
        case (g, j) =>
          s"target_${i}_grad_$j" -> g
      }
  }
}

object TargetGroup {
  def apply(reals: List[Real]): TargetGroup = {
    val parameters =
      reals.toSet
        .flatMap(findParameters)
        .toList
        .sortBy(_.param.sym.id)

    val priors = Real.sum(parameters.map(_.density))
    val targets = (priors :: reals).map { r =>
      val grads = Gradient.derive(parameters, r)
      new Target(r, grads)
    }

    new TargetGroup(targets, parameters)
  }

  def findParameters(real: Real): Set[Parameter] =
    leaves(real).collect { case Left(p) => p }
  def findColumns(real: Real): Set[Column] =
    leaves(real).collect { case Right(c) => c }

  private def leaves(real: Real): Set[Either[Parameter, Column]] = {
    var seen = Set.empty[Real]
    var leaves = List.empty[Either[Parameter, Column]]
    def loop(r: Real): Unit =
      if (!seen.contains(r)) {
        seen += r
        r match {
          case Scalar(_) => ()
          case v: Column =>
            leaves = Right(v) :: leaves
          case v: Parameter =>
            leaves = Left(v) :: leaves
            loop(v.density)
          case u: Unary => loop(u.original)
          case l: Line =>
            l.ax.toList.foreach {
              case (x, a) =>
                loop(x)
                loop(a)
            }
            loop(l.b)
          case l: LogLine =>
            l.ax.toList.foreach {
              case (x, a) =>
                loop(x)
                loop(a)
            }
          case Compare(left, right) =>
            loop(left)
            loop(right)
          case Pow(base, exponent) =>
            loop(base)
            loop(exponent)
          case l: Lookup =>
            loop(l.index)
            l.table.foreach(loop)
        }
      }

    loop(real)

    leaves.toSet
  }
}
