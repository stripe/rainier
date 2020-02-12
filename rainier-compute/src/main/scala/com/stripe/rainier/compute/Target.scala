package com.stripe.rainier.compute

import com.stripe.rainier.ir.GraphViz

case class Target(name: String,
                  real: Real,
                  columns: List[Column],
                  gradient: List[Real],
                  gradientColumns: List[Column])

object Target {
  def apply(name: String, real: Real, parameters: List[Parameter]): Target = {
    val columns = TargetGroup.findColumns(real).toList
    val nRows =
      if (columns.isEmpty)
        0
      else
        columns.head.values.size

    val (real2, columns2) =
      if (nRows > 0 && TargetGroup.inlinable(real))
        (PartialEvaluator.inline(real, nRows), Nil)
      else
        (real, columns)

    val gradient =
      if (parameters.isEmpty) Nil else Gradient.derive(parameters, real2)
    val gradientColumns = gradient.toSet.flatMap { r: Real =>
      TargetGroup.findColumns(r) -- columns2
    }.toList

    Target(name, real2, columns2, gradient, gradientColumns)
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
  def boundsViz(filter: String => Boolean): GraphViz =
    BoundsViz(outputs.filter { case (n, _) => filter(n) })
}

object TargetGroup {
  def apply(reals: List[Real], track: Set[Real]): TargetGroup = {
    val parameters =
      (reals.toSet ++ track)
        .flatMap(findParameters)
        .toList
        .sortBy(_.param.sym.id)

    val priors = parameters.map(_.prior).toSet
    val priorTarget =
      Target("prior", Real.sum(priors.map(_.density)), parameters)
    val others = reals.zipWithIndex.map {
      case (r, i) => Target(s"t_$i", r, parameters)
    }
    new TargetGroup(priorTarget :: others, parameters)
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
            loop(v.prior.density)
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

  //see whether we can reduce this from a function on a matrix of
  //placeholder data (O(N) to compute, where N is the rows in the matrix)
  //to an O(1) function just on the parameters; this should be possible if the function can be expressed
  //as a linear combination of terms that are each functions of either a column,
  //or a parameter, but not both.
  def inlinable(real: Real): Boolean = {
    case class State(hasParameter: Boolean,
                     hasPlaceholder: Boolean,
                     nonlinearCombination: Boolean) {
      def ||(other: State) = State(
        hasParameter || other.hasParameter,
        hasPlaceholder || other.hasPlaceholder,
        nonlinearCombination || other.nonlinearCombination
      )

      def combination = hasParameter && hasPlaceholder

      def nonlinearOp =
        State(hasParameter, hasPlaceholder, combination)

      def inlinable = !nonlinearCombination
    }

    var seen = Map.empty[Real, State]

    def loopMerge(rs: Seq[Real]): State =
      rs.map(loop).reduce(_ || _)

    def loop(r: Real): State =
      if (!seen.contains(r)) {
        val result = r match {
          case Scalar(_) =>
            State(false, false, false)
          case _: Column =>
            State(false, true, false)
          case _: Parameter =>
            State(true, false, false)
          case u: Unary =>
            loopMerge(List(u.original)).nonlinearOp
          case l: Line =>
            loopMerge(l.b :: l.ax.toList.flatMap { case (x, a) => List(x, a) })
          case l: LogLine =>
            val termStates = l.ax.toList.map {
              case (x, a) => loopMerge(List(x, a)).nonlinearOp
            }
            val state = termStates.reduce(_ || _)
            if (state.nonlinearCombination || !state.combination)
              state
            else {
              if (termStates.exists(_.combination))
                state.nonlinearOp
              else
                state
            }
          case Compare(left, right) =>
            loopMerge(List(left, right)).nonlinearOp
          case Pow(base, exponent) =>
            loopMerge(List(base, exponent)).nonlinearOp
          case l: Lookup =>
            val tableState = loopMerge(l.table.toList)
            val indexState = loop(l.index)
            val state = tableState || indexState

            if (indexState.hasParameter)
              state.nonlinearOp
            else
              state
        }
        seen += (r -> result)
        result
      } else {
        seen(r)
      }

    //real+real will trigger a distribute() if warranted
    loop(real + real).inlinable
  }
}
