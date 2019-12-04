package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object RealOps {

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case Infinity =>
        op match {
          case ExpOp => Infinity
          case LogOp => Infinity
          case AbsOp => Infinity
          case SinOp =>
            throw new ArithmeticException(
              "No limit for 'sin' at positive infinity")
          case CosOp =>
            throw new ArithmeticException(
              "No limit for 'cos' at positive infinity")
          case TanOp =>
            throw new ArithmeticException(
              "No limit for 'tan' at positive infinity")
          case AcosOp => throw new ArithmeticException("acos undefined above 1")
          case AsinOp => throw new ArithmeticException("asin undefined above 1")
          case AtanOp => Real.Pi / 2
          case NoOp   => Infinity
        }
      case NegInfinity =>
        op match {
          case ExpOp => Real.zero
          case LogOp =>
            throw new ArithmeticException(
              "Cannot take the log of a negative number")
          case AbsOp => Infinity
          case SinOp =>
            throw new ArithmeticException(
              "No limit for 'sin' at negative infinity")
          case CosOp =>
            throw new ArithmeticException(
              "No limit for 'cos' at negative infinity")
          case TanOp =>
            throw new ArithmeticException(
              "No limit for 'tan' at negative infinity")
          case AcosOp =>
            throw new ArithmeticException("acos undefined below -1")
          case AsinOp =>
            throw new ArithmeticException("asin undefined below -1")
          case AtanOp => -Real.Pi / 2
          case NoOp   => original
        }
      case Constant(Real.BigZero) =>
        op match {
          case ExpOp  => Real.one
          case LogOp  => NegInfinity
          case AbsOp  => Real.zero
          case SinOp  => Real.zero
          case CosOp  => Real.one
          case TanOp  => Real.zero
          case AsinOp => Real.zero
          case AcosOp => Real.Pi / 2
          case AtanOp => Real.zero
          case NoOp   => original
        }
      case Constant(value) =>
        op match {
          case ExpOp => Real(Math.exp(value.toDouble))
          case LogOp =>
            if (value.toDouble < 0)
              throw new ArithmeticException(
                s"Cannot take the log of ${value.toDouble}")
            else
              Real(Math.log(value.toDouble))
          case AbsOp  => Real(value.abs)
          case SinOp  => Real(Math.sin(value.toDouble))
          case CosOp  => Real(Math.cos(value.toDouble))
          case TanOp  => Real(Math.tan(value.toDouble))
          case AsinOp => Real(Math.asin(value.toDouble))
          case AcosOp => Real(Math.acos(value.toDouble))
          case AtanOp => Real(Math.atan(value.toDouble))
          case NoOp   => original
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
      case (Constant(x), nc: NonConstant) => LineOps.translate(nc, x)
      case (nc: NonConstant, Constant(x)) => LineOps.translate(nc, x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(nc1, nc2)
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
      case (NegInfinity, r)               => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (r, NegInfinity)               => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (Infinity, r)                  => Real.gt(r, 0, Infinity, NegInfinity)
      case (r, Infinity)                  => Real.gt(r, 0, Infinity, NegInfinity)
      case (_, Constant(Real.BigZero))    => Real.zero
      case (Constant(Real.BigZero), _)    => Real.zero
      case (_, Constant(Real.BigOne))     => left
      case (Constant(Real.BigOne), _)     => right
      case (Constant(x), Constant(y))     => Real(x * y)
      case (Constant(x), nc: NonConstant) => LineOps.scale(nc, x)
      case (nc: NonConstant, Constant(x)) => LineOps.scale(nc, x)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }

  def divide(left: Real, right: Real): Real =
    (left, right) match {
      case (Constant(Real.BigZero), Constant(Real.BigZero)) =>
        throw new ArithmeticException("Cannot divide zero by zero")
      case (_, Constant(Real.BigZero)) => left * Infinity
      case (Constant(x), Constant(y))  => Real(x / y)
      case _                           => left * right.pow(-1)
    }

  def min(left: Real, right: Real): Real =
    Real.lt(left, right, left, right)

  def max(left: Real, right: Real): Real =
    Real.gt(left, right, left, right)

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
      case (Constant(Real.BigZero), _) if exponent < 0 =>
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
    else if (a < Real.BigZero)
      throw new ArithmeticException(s"Undefined: $a ^ $b")
    else
      BigDecimal(Math.pow(a.toDouble, b.toDouble))

  def compare(left: Real, right: Real): Real =
    (left, right) match {
      case (Infinity, Infinity)       => Real.zero
      case (Infinity, _)              => Real.one
      case (_, Infinity)              => Constant(-1)
      case (NegInfinity, NegInfinity) => Real.zero
      case (NegInfinity, _)           => Constant(-1)
      case (_, NegInfinity)           => Real.one
      case (Constant(a), Constant(b)) =>
        if (a == b)
          Real.zero
        else if (a > b)
          Real.one
        else
          Constant(-1)
      case _ => Compare(left, right)
    }

  def variables(real: Real): Set[Variable] = {
    var seen = Set.empty[Real]
    var vars = List.empty[Variable]
    def loop(r: Real): Unit =
      if (!seen.contains(r)) {
        seen += r
        r match {
          case Constant(_) | Infinity | NegInfinity => ()
          case v: Placeholder                       => vars = v :: vars
          case v: Parameter =>
            vars = v :: vars
            loop(v.density)
          case u: Unary   => loop(u.original)
          case l: Line    => l.ax.terms.foreach(loop)
          case l: LogLine => l.ax.terms.foreach(loop)
          case Compare(left, right) =>
            loop(left)
            loop(right)
          case Pow(base, exponent) =>
            loop(base)
            loop(exponent)
          case l: Lookup =>
            loop(l.index)
            l.table.foreach(loop)
        }
      }

    loop(real)

    vars.toSet
  }
}
