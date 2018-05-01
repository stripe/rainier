package rainier.compute

import scala.collection.mutable.HashMap

private object Gradient {
  def derive(variables: Seq[Variable], output: Real): Seq[Real] = {
    val diffs = HashMap.empty[Real, CompoundDiff]
    def diff(real: Real): CompoundDiff = {
      diffs.getOrElseUpdate(real, new CompoundDiff)
    }

    diff(output).register(new Diff { val toReal = Real.one })

    var visited = Set[Real]()
    def visit(real: Real): Unit = {
      if (!visited.contains(real)) {
        visited += real
        real match {
          case v: Variable     => ()
          case Constant(value) => ()
          case p: Product =>
            diff(p.left).register(ProductDiff(p.right, diff(p)))
            diff(p.right).register(ProductDiff(p.left, diff(p)))
            visit(p.left)
            visit(p.right)

          case u: Unary =>
            diff(u.original).register(UnaryDiff(u, diff(u)))
            visit(u.original)

          case w: Pow =>
            diff(w.original).register(PowDiff(w, diff(w), false))
            diff(w.exponent).register(PowDiff(w, diff(w), true))
            visit(w.original)
            visit(w.exponent)

          case l: Line =>
            l.ax.foreach {
              case (r, d) =>
                diff(r).register(ProductDiff(Constant(d), diff(l)))
            }
            l.ax.foreach { case (r, d) => visit(r) }

          case f: If =>
            diff(f.whenNonZero).register(IfDiff(f, diff(f), true))
            diff(f.whenZero).register(IfDiff(f, diff(f), false))
            visit(f.test)
            visit(f.whenNonZero)
            visit(f.whenZero)
        }
      }
    }

    visit(output)
    variables.map { v =>
      diff(v).toReal
    }
  }

  private sealed trait Diff {
    def toReal: Real
  }

  private class CompoundDiff extends Diff {
    var parts = List.empty[Diff]

    def register(part: Diff): Unit = {
      parts = part :: parts
    }

    def toReal: Real = parts match {
      case head :: Nil => head.toReal
      case _           => Real.sum(parts.map(_.toReal))
    }
  }

  private case class ProductDiff(other: Real, gradient: Diff) extends Diff {
    def toReal = gradient.toReal * other
  }

  private case class UnaryDiff(child: Unary, gradient: Diff) extends Diff {
    def toReal = child.op match {
      case LogOp => gradient.toReal * (Real.one / child.original)
      case ExpOp => gradient.toReal * child
      case AbsOp =>
        If(child.original, gradient.toReal * child.original / child, Real.zero)
    }
  }

  private case class IfDiff(child: If, gradient: Diff, nzBranch: Boolean)
      extends Diff {
    def toReal =
      if (nzBranch)
        If(child.test, gradient.toReal, Real.zero)
      else
        If(child.test, Real.zero, gradient.toReal)
  }

  private case class PowDiff(child: Pow, gradient: Diff, isExponent: Boolean)
      extends Diff {
    def toReal =
      if (isExponent)
        gradient.toReal *
          (If(child.original, child.original, Real.one) * child.original.pow(
            child.exponent)).log
      else
        gradient.toReal *
          child.exponent *
          child.original.pow(If(child.exponent, child.exponent - 1, Real.one))
  }
}
