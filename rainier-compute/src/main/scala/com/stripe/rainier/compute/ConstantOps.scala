package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object ConstantOps {
  import Constant._

  def unary(original: Constant, op: UnaryOp): Constant =
    if (original.isPosInfinity)
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
        case AtanOp => Pi / Two
        case NoOp   => Infinity
      } else if (original.isNegInfinity)
      op match {
        case ExpOp => Zero
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
        case AtanOp => Pi / NegTwo
        case NoOp   => original
      } else if (original.isZero)
      op match {
        case ExpOp  => One
        case LogOp  => NegInfinity
        case AbsOp  => Zero
        case SinOp  => Zero
        case CosOp  => One
        case TanOp  => Zero
        case AsinOp => Zero
        case AcosOp => Pi / Two
        case AtanOp => Zero
        case NoOp   => original
      } else
      op match {
        case ExpOp => original.map(Math.exp)
        case LogOp =>
          if (!original.isPositive)
            throw new ArithmeticException(
              s"Cannot take the log of a negative number")
          else
            original.map(Math.log)
        case AbsOp  => original.map(Math.abs)
        case SinOp  => original.map(Math.sin)
        case CosOp  => original.map(Math.cos)
        case TanOp  => original.map(Math.tan)
        case AsinOp => original.map(Math.asin)
        case AcosOp => original.map(Math.acos)
        case AtanOp => original.map(Math.atan)
        case NoOp   => original
      }

  def add(left: Constant, right: Constant): Constant =
    if ((left.isNegInfinity && right.isPosInfinity) ||
        (left.isPosInfinity && right.isNegInfinity))
      throw new ArithmeticException("Cannot add +inf and -inf")
    else
      left.mapWith(right)(_ + _)

  def multiply(left: Constant, right: Constant): Constant =
    if (((left.isPosInfinity || left.isNegInfinity) && right.isZero) ||
        (left.isZero && (right.isPosInfinity || right.isNegInfinity)))
      throw new ArithmeticException("Cannot multiply inf by zero")
    else
      left.mapWith(right)(_ * _)

  def divide(left: Constant, right: Constant): Constant =
    if (left.isZero && right.isZero)
      throw new ArithmeticException("Cannot divide zero by zero")
    else
      left.mapWith(right)(_ / _)

  def pow(left: Constant, right: Constant): Constant =
    left.mapWith(right) { (x, y) =>
      Math.pow(x, y)
    }

  def compare(left: Constant, right: Constant): Constant =
    left.mapWith(right) { (a, b) =>
      if (a == b || a.isPosInfinity && b.isPosInfinity || a.isNegInfinity && b.isNegInfinity)
        0.0
      else if (a < b || a.isNegInfinity || b.isPosInfinity)
        -1.0
      else
        1.0
    }
}
