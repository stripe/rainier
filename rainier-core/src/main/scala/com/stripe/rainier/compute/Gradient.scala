package com.stripe.rainier.compute

import com.stripe.rainier.ir._
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

          case u: Unary =>
            diff(u.original).register(UnaryDiff(u, diff(u)))
            visit(u.original)

          case l: Line =>
            l.ax.foreach {
              case (x, a) =>
                diff(x).register(ProductDiff(a, diff(l)))
            }
            l.ax.foreach { case (x, _) => visit(x) }

          case l: LogLine =>
            l.ax.foreach {
              case (x, a) =>
                diff(x).register(LogLineDiff(l, diff(l), x))
            }
            l.ax.foreach { case (x, _) => visit(x) }

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

  private case class ProductDiff(other: Double, gradient: Diff) extends Diff {
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

  private case class LogLineDiff(child: LogLine, gradient: Diff, term: Real)
      extends Diff {
    def toReal = {
      val exponent = child.ax(term)
      val otherTerms =
        if (child.ax.size == 1)
          Real.one
        else
          LogLine(child.ax - term)
      gradient.toReal *
        exponent *
        term.pow(exponent - 1) *
        otherTerms
    }
  }
}
