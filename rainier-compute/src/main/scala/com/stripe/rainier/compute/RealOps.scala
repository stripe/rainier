package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object RealOps {
  import Constant._

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case c: Constant => ConstantOps.unary(c, op)
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
      case (x: Constant, y: Constant)     => ConstantOps.add(x, y)
      case (Infinity, _)                  => left
      case (_, Infinity)                  => right
      case (NegInfinity, _)               => left
      case (_, NegInfinity)               => right
      case (_, Zero)                      => left
      case (Zero, _)                      => right
      case (c: Constant, nc: NonConstant) => LineOps.translate(nc, c)
      case (nc: NonConstant, c: Constant) => LineOps.translate(nc, c)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(nc1, nc2)
    }

  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (x: Constant, y: Constant)     => ConstantOps.multiply(x, y)
      case (Infinity, r)                  => Real.gt(r, 0, Infinity, NegInfinity)
      case (r, Infinity)                  => Real.gt(r, 0, Infinity, NegInfinity)
      case (NegInfinity, r)               => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (r, NegInfinity)               => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (_, Zero)                      => Real.zero
      case (Zero, _)                      => Real.zero
      case (_, One)                       => left
      case (One, _)                       => right
      case (c: Constant, nc: NonConstant) => LineOps.scale(nc, c)
      case (nc: NonConstant, c: Constant) => LineOps.scale(nc, c)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }

  def divide(left: Real, right: Real): Real =
    (left, right) match {
      case (x: Constant, y: Constant) => ConstantOps.divide(x, y)
      case (_, Zero)                  => left * Infinity
      case _                          => left * right.pow(-1)
    }

  def min(left: Real, right: Real): Real =
    Real.lt(left, right, left, right)

  def max(left: Real, right: Real): Real =
    Real.gt(left, right, left, right)

  def pow(original: Real, exponent: Real): Real =
    exponent match {
      case c: Constant    => pow(original, c)
      case e: NonConstant => Pow(original, e)
    }

  def pow(original: Real, exponent: Constant): Real =
    (original, exponent) match {
      case (c: Constant, _) => ConstantOps.pow(c, exponent)
      case (_, Infinity)    => Infinity
      case (_, NegInfinity) => Zero
      case (_, Zero)        => One
      case (_, One)         => original
      case (l: Line, _) =>
        LineOps.pow(l, exponent).getOrElse {
          LogLineOps.pow(LogLine(l), exponent)
        }
      case (nc: NonConstant, _) =>
        LogLineOps.pow(LogLine(nc), exponent)
    }

  def compare(left: Real, right: Real): Real =
    (left, right) match {
      case (x: Constant, y: Constant) => ConstantOps.compare(x, y)
      case (Infinity, _)              => One
      case (_, Infinity)              => NegOne
      case (NegInfinity, _)           => NegOne
      case (_, NegInfinity)           => One
      case _                          => Compare(left, right)
    }
}
