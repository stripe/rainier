package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object RealOps {

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case Infinity => Infinity
      case NegInfinity =>
        op match {
          case ExpOp => Real.zero
          case LogOp =>
            throw new ArithmeticException(
              "Cannot take the log of a negative number")
          case AbsOp => Infinity
        }
      case Constant(Real.BigZero) =>
        op match {
          case ExpOp => Real.one
          case LogOp => NegInfinity
          case AbsOp => Real.zero
        }
      case Constant(value) =>
        op match {
          case ExpOp => Real(Math.exp(value.toDouble))
          case LogOp =>
            if (value.toDouble < 0)
              throw new ArithmeticException(
                "Cannot take the log of " + value.toDouble)
            else
              Real(Math.log(value.toDouble))
          case AbsOp => Real(value.abs)
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
      case (Infinity, NegInfinity) =>
        throw new ArithmeticException("Cannot add +inf and -inf")
      case (NegInfinity, Infinity) =>
        throw new ArithmeticException("Cannot add +inf and -inf")
      case (Infinity, _)                  => Infinity
      case (_, Infinity)                  => Infinity
      case (NegInfinity, _)               => NegInfinity
      case (_, NegInfinity)               => NegInfinity
      case (_, Constant(Real.BigZero))    => left
      case (Constant(Real.BigZero), _)    => right
      case (Constant(x), Constant(y))     => Real(x + y)
      case (Constant(x), nc: NonConstant) => LineOps.translate(Line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.translate(Line(nc), x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(Line(nc1), Line(nc2))
    }

  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (NegInfinity, NegInfinity) => Infinity
      case (NegInfinity, Constant(Real.BigZero)) =>
        throw new ArithmeticException("Cannot multiply -inf by zero")
      case (Infinity, Constant(Real.BigZero)) =>
        throw new ArithmeticException("Cannot multiply +inf by zero")
      case (Constant(Real.BigZero), NegInfinity) =>
        throw new ArithmeticException("Cannot multiply -inf by zero")
      case (Constant(Real.BigZero), Infinity) =>
        throw new ArithmeticException("Cannot multiply +inf by zero")
      case (NegInfinity, r)               => If(r > 0, NegInfinity, Infinity)
      case (r, NegInfinity)               => If(r > 0, NegInfinity, Infinity)
      case (Infinity, r)                  => If(r > 0, Infinity, NegInfinity)
      case (r, Infinity)                  => If(r > 0, Infinity, NegInfinity)
      case (_, Constant(Real.BigZero))    => Real.zero
      case (Constant(Real.BigZero), _)    => Real.zero
      case (_, Constant(Real.BigOne))     => left
      case (Constant(Real.BigOne), _)     => right
      case (Constant(x), Constant(y))     => Real(x * y)
      case (Constant(x), nc: NonConstant) => LineOps.scale(Line(nc), x)
      case (nc: NonConstant, Constant(x)) => LineOps.scale(Line(nc), x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }

  def pow(original: Real, exponent: Real): Real =
    exponent match {
      case Infinity       => Infinity
      case NegInfinity    => Real.zero
      case Constant(e)    => pow(original, e)
      case e: NonConstant => Pow(original, e)
    }

  def pow(original: Real, exponent: BigDecimal): Real =
    (original, exponent) match {
      case (_, Real.BigZero) => Real.one
      case (_, Real.BigOne)  => original
      case (Infinity, _) =>
        if (exponent < Real.BigZero)
          Real.zero
        else
          Infinity
      case (NegInfinity, _) =>
        if (exponent < Real.BigZero)
          Real.zero
        else if (exponent.isWhole && exponent.toInt % 2 == 1)
          NegInfinity
        else
          Infinity
      case (Constant(v), _) => Real(pow(v, exponent))
      case (l: Line, _) =>
        LineOps.pow(l, exponent).getOrElse {
          LogLineOps.pow(LogLine(l), exponent)
        }
      case (nc: NonConstant, _) =>
        LogLineOps.pow(LogLine(nc), exponent)
    }

  def pow(a: BigDecimal, b: BigDecimal): BigDecimal =
    if (b.isValidInt)
      a.pow(b.toInt)
    else
      BigDecimal(Math.pow(a.toDouble, b.toDouble))

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
          case Constant(_) | Infinity | NegInfinity => ()
          case v: Variable                          => vars = v :: vars
          case u: Unary                             => loop(u.original)
          case l: Line                              => l.ax.keys.foreach(loop)
          case l: LogLine                           => l.ax.keys.foreach(loop)
          case If(test, nz, z) =>
            loop(test)
            loop(nz)
            loop(z)
          case Pow(base, exponent) =>
            loop(base)
            loop(exponent)
        }
      }

    loop(real)

    vars.sortBy(_.param.sym.id)
  }
}
