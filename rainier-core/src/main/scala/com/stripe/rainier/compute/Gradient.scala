package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import scala.collection.mutable.HashMap

object Gradient {
  def derive(variables: Seq[Variable], output: Real): Seq[Real] = {
    val diffs = HashMap.empty[Real, CompoundDiff]
    def diff(real: Real): CompoundDiff = {
      diffs.getOrElseUpdate(real, new CompoundDiff)
    }

    diff(output).register(new Diff {
      val toReal: Real = Real.one
    })

    var visited = Set[Real]()
    def visit(real: Real): Unit = {
      if (!visited.contains(real)) {
        visited += real
        real match {
          case _: Variable            => ()
          case _: Constant            => ()
          case Infinity | NegInfinity => ()

          case p: Pow =>
            diff(p.base).register(PowDiff(p, diff(p), false))
            diff(p.exponent).register(PowDiff(p, diff(p), true))
            visit(p.base)
            visit(p.exponent)

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
              case (x, _) =>
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

  private sealed class CompoundDiff extends Diff {
    var parts: List[Diff] = List.empty[Diff]

    def register(part: Diff): Unit = {
      parts = part :: parts
    }

    lazy val toReal: Real = parts match {
      case head :: Nil => head.toReal
      case _ =>
        Real.sum(parts.map(_.toReal))
    }
  }

  private final case class ProductDiff(other: BigDecimal, gradient: Diff)
      extends Diff {
    def toReal: Real = gradient.toReal * Constant(other)
  }

  private final case class UnaryDiff(child: Unary, gradient: Diff)
      extends Diff {
    def toReal: Real = child.op match {
      case LogOp => gradient.toReal * (Real.one / child.original)
      case ExpOp => gradient.toReal * child
      case AbsOp =>
        If(child.original, gradient.toReal * child.original / child, Real.zero)
    }
  }

  private final case class IfDiff(child: If, gradient: Diff, nzBranch: Boolean)
      extends Diff {
    def toReal: Real =
      if (nzBranch)
        If(child.test, gradient.toReal, Real.zero)
      else
        If(child.test, Real.zero, gradient.toReal)
  }

  private final case class PowDiff(child: Pow,
                                   gradient: Diff,
                                   isExponent: Boolean)
      extends Diff {

    def toReal: Real =
      if (isExponent)
        gradient.toReal * child * If(child.base, child.base, Real.one).log
      else
        gradient.toReal * child.exponent * child.base.pow(child.exponent - 1)
  }

  private final case class LogLineDiff(child: LogLine,
                                       gradient: Diff,
                                       term: NonConstant)
      extends Diff {
    def toReal: Real = {
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
