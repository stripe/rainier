package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object RealOps {

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case Constant(value) =>
        op match {
          case ExpOp => Constant(Math.exp(value))
          case LogOp => Constant(Math.log(value))
          case AbsOp => Constant(Math.abs(value))
        }
      case nc: NonConstant =>
        val opt = (op, nc) match {
          case (ExpOp, Unary(x, LogOp))     => Some(x)
          case (AbsOp, u @ Unary(_, AbsOp)) => Some(u)
          case (AbsOp, u @ Unary(_, ExpOp)) => Some(u)
          case (LogOp, Unary(x, ExpOp))     => Some(x)
          case (LogOp, l: Line)             => LineOps.log(l)
          case (LogOp, l: LogLine)          => LogLineOps.log(l)
          case _                            => None
        }
        opt.getOrElse(Unary(nc, op))
    }

  def add(left: Real, right: Real): Real =
    (left, right) match {
      case (_, Constant(0.0))             => left
      case (Constant(0.0), _)             => right
      case (Constant(x), Constant(y))     => Constant(x + y)
      case (Constant(x), nc: NonConstant) => LineOps.translate(Line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.translate(Line(nc), x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(Line(nc1), Line(nc2))
    }

  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (_, Constant(0.0))             => Real.zero
      case (Constant(0.0), _)             => Real.zero
      case (_, Constant(1.0))             => left
      case (Constant(1.0), _)             => right
      case (Constant(x), Constant(y))     => Constant(x * y)
      case (Constant(x), nc: NonConstant) => LineOps.scale(Line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.scale(Line(nc), x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }

  def pow(original: Real, exponent: Double): Real =
    (original, exponent) match {
      case (Constant(v), _) => Constant(Math.pow(v, exponent))
      case (_, 0.0)         => Real.one
      case (_, 1.0)         => original
      case (l: Line, _) =>
        LineOps.pow(l, exponent).getOrElse {
          LogLineOps.pow(LogLine(l), exponent)
        }
      case (nc: NonConstant, _) =>
        LogLineOps.pow(LogLine(nc), exponent)
    }

  def isPositive(real: Real): Real =
    If(real, nonZeroIsPositive(real), Real.zero)

  def isNegative(real: Real): Real =
    If(real, Real.one - nonZeroIsPositive(real), Real.zero)

  private def nonZeroIsPositive(real: Real): Real =
    ((real.abs / real) + 1) / 2

  def variables(real: Real): List[Variable] = {
    var seen = Set.empty[Real]
    var vars = List.empty[Variable]
    def loop(r: Real): Unit =
      if (!seen.contains(r)) {
        seen += r
        r match {
          case Constant(_) => ()
          case v: Variable => vars = v :: vars
          case u: Unary    => loop(u.original)
          case l: Line     => l.ax.keys.foreach(loop)
          case l: LogLine  => l.ax.keys.foreach(loop)
          case If(test, nz, z) =>
            loop(test)
            loop(nz)
            loop(z)
        }
      }

    loop(real)

    vars.sortBy(_.param.sym.id)
  }
}
