package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import java.util.IdentityHashMap

private object Gradient {
  def derive(variables: Seq[Variable], output: Real): Seq[Real] = {
    val diffs = new IdentityHashMap[Real, CompoundDiff]
    def diff(real: Real): CompoundDiff = {
      Option(diffs.get(real)) match {
        case Some(d) => d
        case None =>
          val d = new CompoundDiff
          if (diffs.size % 1000 == 0)
            println(diffs.size)
          diffs.put(real, d)
          d
      }
    }

    diff(output).register(new Diff {
      val toReal: Real = Real.one
    })

    def loop(real: Real): Unit =
      if (!diffs.containsKey(real)) {
        println(real)
        visit(real)
      }

    def visit(real: Real): Unit =
      real match {
        case _: Variable            => ()
        case _: Constant            => ()
        case Infinity | NegInfinity => ()

        case p: Pow =>
          diff(p.base).register(PowDiff(p, diff(p), false))
          diff(p.exponent).register(PowDiff(p, diff(p), true))
          loop(p.base)
          loop(p.exponent)

        case u: Unary =>
          diff(u.original).register(UnaryDiff(u, diff(u)))
          loop(u.original)

        case l: Line =>
          l.ax.foreach {
            case (x, a) =>
              diff(x).register(ProductDiff(a, diff(l)))
          }
          l.ax.foreach { case (x, _) => loop(x) }

        case l: BigLine =>
          l.a.foreach { x =>
            diff(x).register(diff(l))
          }
          l.a.foreach(loop)

        case l: LogLine =>
          l.ax.foreach {
            case (x, _) =>
              diff(x).register(LogLineDiff(l, diff(l), x))
          }
          l.ax.foreach { case (x, _) => loop(x) }

        case f: If =>
          diff(f.whenNonZero).register(IfDiff(f, diff(f), true))
          diff(f.whenZero).register(IfDiff(f, diff(f), false))
          loop(f.test)
          loop(f.whenNonZero)
          loop(f.whenZero)
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
