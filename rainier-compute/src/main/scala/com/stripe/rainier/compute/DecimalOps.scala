package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import scala.annotation.tailrec

object DecimalOps {
  import Decimal._

  def add(x: Decimal, y: Decimal): Decimal = (x, y) match {
    case (Infinity, NegInfinity) =>
      throw new ArithmeticException("Cannot add +inf and -inf")
    case (NegInfinity, Infinity) =>
      throw new ArithmeticException("Cannot add +inf and -inf")
    case (Infinity, _) =>
      Infinity
    case (NegInfinity, _) =>
      NegInfinity
    case (_, Infinity) =>
      Infinity
    case (_, NegInfinity) =>
      NegInfinity
    case (d: DoubleDecimal, _) => Decimal(d.toDouble + y.toDouble)
    case (_, d: DoubleDecimal) => Decimal(d.toDouble + x.toDouble)
    case (f: FractionDecimal, g: FractionDecimal) => {
      val d = lcm(f.d, g.d)
      val n = (f.n * d / f.d) + (g.n * d / g.d)
      new FractionDecimal(n, d)
    }
  }

  def subtract(x: Decimal, y: Decimal): Decimal = (x, y) match {
    case (Infinity, Infinity) =>
      throw new ArithmeticException("Cannot subtract inf and inf")
    case (NegInfinity, NegInfinity) =>
      throw new ArithmeticException("Cannot subtract -inf and -inf")
    case (Infinity, _) =>
      Infinity
    case (NegInfinity, _) =>
      NegInfinity
    case (_, Infinity) =>
      NegInfinity
    case (_, NegInfinity) =>
      Infinity
    case (d: DoubleDecimal, _) => Decimal(d.toDouble - y.toDouble)
    case (_, d: DoubleDecimal) => Decimal(x.toDouble - d.toDouble)
    case (f: FractionDecimal, g: FractionDecimal) => {
      val d = lcm(f.d, g.d)
      val n = (f.n * d / f.d) - (g.n * d / g.d)
      new FractionDecimal(n, d)
    }
  }

  def multiply(x: Decimal, y: Decimal): Decimal = (x, y) match {
    case (NegInfinity, Zero) =>
      throw new ArithmeticException("Cannot multiply -inf by zero")
    case (Infinity, Zero) =>
      throw new ArithmeticException("Cannot multiply +inf by zero")
    case (Zero, NegInfinity) =>
      throw new ArithmeticException("Cannot multiply -inf by zero")
    case (Zero, Infinity) =>
      throw new ArithmeticException("Cannot multiply +inf by zero")
    case (Infinity, _) =>
      if (y > Zero)
        Infinity
      else
        NegInfinity
    case (_, Infinity) =>
      if (x > Zero)
        Infinity
      else
        NegInfinity
    case (NegInfinity, _) =>
      if (y > Zero)
        NegInfinity
      else
        Infinity
    case (_, NegInfinity) =>
      if (x > Zero)
        NegInfinity
      else
        Infinity
    case (d: DoubleDecimal, _) => Decimal(d.toDouble * y.toDouble)
    case (_, d: DoubleDecimal) => Decimal(x.toDouble * d.toDouble)
    case (f: FractionDecimal, g: FractionDecimal) => {
      val n = f.n * g.n
      val d = f.d * g.d
      val gc = gcd(n, d)
      new FractionDecimal(n / gc, d / gc)
    }
  }

  def divide(x: Decimal, y: Decimal): Decimal = (x, y) match {
    case (Zero, Zero) =>
      throw new ArithmeticException("Cannot divide zero by zero")
    case (Infinity, NegInfinity) =>
      throw new ArithmeticException("Cannot divide inf by -inf")
    case (NegInfinity, Infinity) =>
      throw new ArithmeticException("Cannot divide -inf by inf")
    case (Infinity, Infinity) =>
      throw new ArithmeticException("Cannot divide inf by inf")
    case (NegInfinity, NegInfinity) =>
      throw new ArithmeticException("Cannot divide -inf by -inf")
    case (Infinity, _) =>
      if (y > Zero)
        Infinity
      else
        NegInfinity
    case (NegInfinity, _) =>
      if (y > Zero)
        NegInfinity
      else
        Infinity
    case (_, Infinity) =>
      Zero
    case (_, NegInfinity) =>
      Zero
    case (d: DoubleDecimal, _) => Decimal(d.toDouble / y.toDouble)
    case (_, d: DoubleDecimal) => Decimal(x.toDouble / d.toDouble)
    case (f: FractionDecimal, g: FractionDecimal) =>
      val n = f.n * g.d
      val d = f.d * g.n
      val gc = gcd(n, d)
      new FractionDecimal(n / gc, d / gc)
  }

  def abs(x: Decimal): Decimal = x match {
    case Infinity           => Infinity
    case NegInfinity        => Infinity
    case d: DoubleDecimal   => Decimal(Math.abs(d.toDouble))
    case f: FractionDecimal => new FractionDecimal(f.n.abs, f.d.abs)
  }

  def pow(x: Decimal, y: Int): Decimal = x match {
    case Infinity =>
      if (y == 0) One
      else if (y > 0) Infinity
      else Zero
    case NegInfinity =>
      if (y > 0) {
        if (y % 2 == 0)
          Infinity
        else
          NegInfinity
      } else
        Zero
    case d: DoubleDecimal => Decimal(Math.pow(d.toDouble, y.toDouble))
    case f: FractionDecimal =>
      val yabs = Math.abs(y).toDouble
      val n2 = Math.pow(f.n.toDouble, yabs)
      val d2 = Math.pow(f.d.toDouble, yabs)
      if (y >= 0)
        new FractionDecimal(n2, d2)
      else
        new FractionDecimal(d2, n2)
  }

  def pow(a: Decimal, b: Decimal): Decimal =
    if (b.isValidInt)
      pow(a, b.toInt)
    else if (a < Zero)
      throw new ArithmeticException(s"Undefined: $a ^ $b")
    else
      Decimal(Math.pow(a.toDouble, b.toDouble))

  def unary(x: Decimal, op: UnaryOp): Decimal =
    x match {
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
          case AtanOp => Pi / Decimal(2)
          case NoOp   => Infinity
        }
      case NegInfinity =>
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
          case AtanOp => Pi / Decimal(-2)
          case NoOp   => x
        }
      case Zero =>
        op match {
          case ExpOp  => One
          case LogOp  => NegInfinity
          case AbsOp  => Zero
          case SinOp  => Zero
          case CosOp  => One
          case TanOp  => Zero
          case AsinOp => Zero
          case AcosOp => Pi / Decimal(2)
          case AtanOp => Zero
          case NoOp   => x
        }
      case _ =>
        op match {
          case ExpOp => Decimal(Math.exp(x.toDouble))
          case LogOp =>
            if (x.toDouble < 0)
              throw new ArithmeticException(
                s"Cannot take the log of ${x.toDouble}")
            else
              Decimal(Math.log(x.toDouble))
          case AbsOp  => abs(x)
          case SinOp  => Decimal(Math.sin(x.toDouble))
          case CosOp  => Decimal(Math.cos(x.toDouble))
          case TanOp  => Decimal(Math.tan(x.toDouble))
          case AsinOp => Decimal(Math.asin(x.toDouble))
          case AcosOp => Decimal(Math.acos(x.toDouble))
          case AtanOp => Decimal(Math.atan(x.toDouble))
          case NoOp   => x
        }
    }

  def compare(a: Decimal, b: Decimal): Decimal = {
    if (a == b)
      Zero
    else if (a > b)
      One
    else
      Decimal(-1)
  }

  private def lcm(x: Double, y: Double): Double = {
    (x * y) / gcd(x, y)
  }

  @tailrec
  private def gcd(x: Double, y: Double): Double = {
    if (y == 0)
      x.abs
    else
      gcd(y, x % y)
  }

}
