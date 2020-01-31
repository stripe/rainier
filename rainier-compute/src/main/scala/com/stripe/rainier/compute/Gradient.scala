package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import scala.collection.mutable.HashMap

private object Gradient {

  def derive(parameters: List[Parameter], output: Real): List[Real] = {
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
          case _: Parameter => ()
          case _: Constant  => ()

          case p: Pow =>
            diff(p.base).register(PowDiff(p, diff(p), false))
            diff(p.exponent).register(PowDiff(p, diff(p), true))
            visit(p.base)
            visit(p.exponent)

          case u: Unary =>
            diff(u.original).register(UnaryDiff(u, diff(u)))
            visit(u.original)

          case l: Line =>
            l.ax.toList.foreach {
              case (x, a) =>
                diff(x).register(ProductDiff(a, diff(l)))
                visit(x)
            }

          case l: LogLine =>
            l.ax.withComplements.foreach {
              case (x, a, c) =>
                diff(x).register(LogLineDiff(diff(l), x, a, c))
                visit(x)
            }

          case l: Lookup =>
            l.table.zipWithIndex.foreach {
              case (x, i) =>
                diff(x).register(LookupDiff(l, diff(l), i + l.low))
                visit(x)
            }
            visit(l.index)

          case c: Compare =>
            //no gradient
            visit(c.left)
            visit(c.right)
        }
      }
    }

    visit(output)

    parameters.map(v => diff(v).toReal)
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

  private final case class ProductDiff(other: Constant, gradient: Diff)
      extends Diff {
    def toReal: Real = gradient.toReal * other
  }

  private final case class UnaryDiff(child: Unary, gradient: Diff)
      extends Diff {
    def toReal: Real = child.op match {
      case LogOp => gradient.toReal * (Real.one / child.original)
      case ExpOp => gradient.toReal * child
      case AbsOp =>
        Real.eq(child.original,
                Real.zero,
                Real.zero,
                gradient.toReal * child.original / child)
      case NoOp  => gradient.toReal
      case SinOp => gradient.toReal * child.original.cos
      case CosOp => gradient.toReal * (Real.zero - child.original.sin)
      case TanOp => gradient.toReal / child.original.cos.pow(2)
      case AsinOp =>
        gradient.toReal / (Real.one - child.original.pow(2)).pow(0.5)
      case AcosOp =>
        -gradient.toReal / (Real.one - child.original.pow(2)).pow(0.5)
      case AtanOp =>
        gradient.toReal / (Real.one + child.original.pow(2))
    }
  }

  private final case class PowDiff(child: Pow,
                                   gradient: Diff,
                                   isExponent: Boolean)
      extends Diff {

    def toReal: Real =
      if (isExponent)
        gradient.toReal * child *
          Real.eq(child.base, Real.zero, Real.one, child.base).log
      else
        gradient.toReal * child.exponent * child.base.pow(child.exponent - 1)
  }

  private final case class LogLineDiff(gradient: Diff,
                                       term: NonConstant,
                                       exponent: Constant,
                                       complement: Coefficients)
      extends Diff {
    def toReal: Real = {
      val otherTerms =
        if (complement.isEmpty)
          Real.one
        else
          LogLine(complement)
      gradient.toReal *
        exponent *
        term.pow(exponent - Real.one) *
        otherTerms
    }
  }

  private final case class LookupDiff(child: Lookup, gradient: Diff, index: Int)
      extends Diff {
    def toReal: Real =
      Real.eq(child.index, index, gradient.toReal, Real.zero)
  }
}
