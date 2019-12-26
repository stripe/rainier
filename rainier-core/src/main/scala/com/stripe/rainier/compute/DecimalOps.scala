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
        case (DoubleDecimal(v), _) => DoubleDecimal(v + y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(v + x.toDouble)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val d = lcm(d1, d2)
          val n = (n1 * d / d1) + (n2 * d / d2)
          FractionDecimal(n, d)
        }
      }

      def subtract(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (Infinity, Infinity) =>
          throw new ArithmeticException("Cannot subtract inf and inf")
        case (NegInfinity, NegInfinity) =>
          throw new ArithmeticException("Cannot subtract -inf and -inf")
        case (DoubleDecimal(v), _) => DoubleDecimal(v - y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble - v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val d = lcm(d1, d2)
          val n = (n1 * d / d1) - (n2 * d / d2)
          FractionDecimal(n, d)
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
        case (DoubleDecimal(v), _) => DoubleDecimal(v * y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble * v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val n = n1 * n2
          val d = d1 * d2
          val g = gcd(n, d)
          FractionDecimal(n / g, d / g)
        }
      }
    
      def divide(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (Zero, Zero) =>
          throw new ArithmeticException("Cannot divide zero by zero")        case (DoubleDecimal(v), _) => DoubleDecimal(v / y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble / v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) =>
          val n = n1 * d2
          val d = d1 * n2
          val g = gcd(n, d)
          FractionDecimal(n / g, d / g)
      }

      def abs(x: Decimal): Decimal = x match {
        case DoubleDecimal(v)      => DoubleDecimal(Math.abs(v))
        case FractionDecimal(n, d) => FractionDecimal(n.abs, d.abs)
      }
    
      def pow(x: Decimal, y: Int): Decimal = x match {
        case DoubleDecimal(v) => DoubleDecimal(Math.pow(v, y.toDouble))
        case FractionDecimal(n, d) =>
          val yabs = Math.abs(y).toDouble
          val n2 = Math.pow(n.toDouble, yabs).toLong
          val d2 = Math.pow(d.toDouble, yabs).toLong
          if (y >= 0)
            FractionDecimal(n2, d2)
          else
            FractionDecimal(d2, n2)
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
          ???
        /*
                if (a == b)
          Real.zero
        else if (a > b)
          Real.one
        else
          Constant(Decimal(-1))
      case (NegInfinity, NegInfinity) => Real.zero

                case (Infinity, Infinity)       => Real.zero

          */


          /*pow

                case (Infinity, _) =>
        if (exponent < Decimal.Zero)
          Real.zero
        else
          Infinity
      case (NegInfinity, _) =>
        if (exponent < Decimal.Zero)
          Zero
        else if (exponent.isWhole && exponent.toInt % 2 == 1)
          NegInfinity
        else
          Infinity
      case (Zero, _) if exponent < Decimal.Zero =>
        Infinity



      */

      }

      private def lcm(x: Long, y: Long): Long = {
        (x * y) / gcd(x, y)
      }
    
      @tailrec
      private def gcd(x: Long, y: Long): Long = {
        if (y == 0)
          x.abs
        else
          gcd(y, x % y)
      }
    
}