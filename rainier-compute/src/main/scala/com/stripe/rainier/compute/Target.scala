package com.stripe.rainier.compute

import com.stripe.rainier.ir.GraphViz

case class Target(name: String, real: Real, gradient: List[Real]) {
  val columns: List[Column] = TargetGroup.findColumns(real).toList
  val gradientColumns: List[Column] = gradient.toSet.flatMap { r: Real =>
    TargetGroup.findColumns(r) -- columns
  }.toList
}

object Target {
  def derive(name: String, real: Real, parameters: List[Parameter]): Target = {
    if (parameters.isEmpty)
      Target(name, real, Nil)
    else
      Target(name, real, Gradient.derive(parameters, real))
  }
}

class TargetGroup(targets: List[Target], val parameters: List[Parameter]) {

  val columns = targets.flatMap { t =>
    t.columns ++ t.gradientColumns
  }
  val inputs = parameters.map(_.param) ++ columns.map(_.param)

  val data =
    targets.map { target =>
      (target.columns ++ target.gradientColumns).map { v =>
        v.values
      }.toArray
    }.toArray

  val outputs = targets.flatMap { t =>
    (t.name -> t.real) ::
      t.gradient.zipWithIndex.map {
      case (g, i) =>
        s"${t.name}_grad_$i" -> g
    }
  }

  def graphViz: GraphViz = RealViz(outputs)
  def graphViz(filter: String => Boolean): GraphViz =
    RealViz(outputs.filter { case (n, _) => filter(n) })
}

object TargetGroup {
  def apply(reals: List[Real]): TargetGroup = {
    val parameters =
      reals.toSet
        .flatMap(findParameters)
        .toList
        .sortBy(_.param.sym.id)

    val priors =
      Target.derive("prior", Real.sum(parameters.map(_.density)), parameters)
    val others = reals.zipWithIndex.map {
      case (r, i) => Target.derive(s"t_$i", r, parameters)
    }
    new TargetGroup(priors :: others, parameters)
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
