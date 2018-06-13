package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import scala.collection.mutable.HashMap

private object Gradient {
  def derive(output: Real): List[(Variable, Real)] = {
    val diffs = HashMap.empty[Real, CompoundDiff]
    var variableDiffs = List.empty[(Variable, CompoundDiff)]

    def register(real: Real, part: Diff): Unit =
      (real, diffs.get(real)) match {
        case (_, Some(d)) =>
          d.register(part)
        case (v: Variable, None) =>
          val d = new CompoundDiff
          diffs.update(v, d)
          d.register(part)
          variableDiffs = (v, d) :: variableDiffs
        case (nc: NonConstant, None) =>
          val d = new CompoundDiff
          diffs.update(nc, d)
          d.register(part)
          visit(nc, d)
        case _ => ()
      }

    def visit(real: NonConstant, gradient: Diff): Unit =
      real match {
        case _: Variable => ()

        case p: Pow =>
          register(p.base, PowDiff(p, gradient, false))
          register(p.exponent, PowDiff(p, gradient, true))

        case u: Unary =>
          register(u.original, UnaryDiff(u, gradient))

        case l: Line =>
          l.ax.foreach {
            case (x, a) =>
              register(x, ProductDiff(a, gradient))
          }

        case l: LogLine =>
          l.ax.foreach {
            case (x, _) =>
              register(x, LogLineDiff(l, gradient, x))
          }

        case f: If =>
          register(f.whenNonZero, IfDiff(f, gradient, true))
          register(f.whenZero, IfDiff(f, gradient, false))
          register(f.test, new Diff { val toReal = Real.zero })
      }

    register(output, new Diff {
      val toReal: Real = Real.one
    })

    variableDiffs.map {
      case (v, d) =>
        (v, d.toReal)
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
