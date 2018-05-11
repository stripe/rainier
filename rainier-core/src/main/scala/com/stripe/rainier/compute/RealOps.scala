package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private object RealOps {

  def add(left: Real, right: Real): Real =
    LineOps.sum(Line(left), Line(right))

  def unary(original: Real, op: UnaryOp): Real = {
    val optimized =
      (op, original) match {
        case (ExpOp, Constant(value))     => Some(Real(Math.exp(value)))
        case (LogOp, Constant(value))     => Some(Real(Math.log(value)))
        case (AbsOp, Constant(value))     => Some(Real(Math.abs(value)))
        case (ExpOp, Unary(x, LogOp))     => Some(x)
        case (AbsOp, u @ Unary(_, AbsOp)) => Some(u)
        case (AbsOp, u @ Unary(_, ExpOp)) => Some(u)
        case (LogOp, Unary(x, ExpOp))     => Some(x)
        case (LogOp, l: Line)             => LineOps.log(l)
        case (LogOp, l: LogLine)          => LogLineOps.log(l)
        case _                            => None
      }
    optimized.getOrElse(Unary(original, op))
  }

  /*
  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (_, Constant(0.0))         => Real.zero
      case (Constant(0.0), _)         => Real.zero
      case (_, Constant(1.0))         => left
      case (Constant(1.0), _)         => right
      case (Constant(x), Constant(y)) => Constant(x * y)
      case (Constant(x), nc: Real)    => LineOps.scale(Line(nc), x)
      case (nc: Real, Constant(x))    => LineOps.scale(Line(nc), x)
      case (l1: Line, l2: Line) =>
        LineOps.multiply(l1, l2).getOrElse {
          LogLineOps.multiply(LogLine(l1), LogLine(l2))
        }
      case (nc1: Real, nc2: Real) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }
   */

  def multiply(left: Real, right: Real): Real = {
    val optimized =
      (left, right) match {
        case (l: Line, _) => LineOps.multiply(l, Line(right))
        case (_, l: Line) => LineOps.multiply(Line(left), l)
        case _            => None
      }
    optimized.getOrElse {
      LogLineOps.multiply(LogLine(left), LogLine(right))
    }
  }

  def pow(original: Real, exponent: Double): Real = {
    val optimized =
      (original, exponent) match {
        case (Constant(v), _) => Some(Real(Math.pow(v, exponent)))
        case (_, 0.0)         => Some(Real.one)
        case (_, 1.0)         => Some(original)
        case (l: Line, _)     => LineOps.pow(l, exponent)
        case _                => None
      }
    optimized.getOrElse {
      LogLineOps.pow(LogLine(original), exponent)
    }
  }

  def isPositive(real: Real): Real =
    If(real, nonZeroIsPositive(real), Real.zero)

  def isNegative(real: Real): Real =
    If(real, Real.one - nonZeroIsPositive(real), Real.zero)

  private def nonZeroIsPositive(real: Real): Real =
    ((real.abs / real) + 1) / 2

  def variables(real: Real): Set[Variable] = {
    def loop(r: Real, acc: Set[Variable]): Set[Variable] =
      r match {
        case v: Variable => acc + v
        case u: Unary    => loop(u.original, acc)
        case l: Line     => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case l: LogLine  => l.ax.foldLeft(acc) { case (a, (r, d)) => loop(r, a) }
        case If(test, nz, z) =>
          val acc2 = loop(test, acc)
          val acc3 = loop(nz, acc2)
          loop(z, acc3)
      }

    loop(real, Set.empty)
  }
}
