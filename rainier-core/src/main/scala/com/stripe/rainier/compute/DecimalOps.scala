package com.stripe.rainier.compute

import scala.annotation.tailrec

object DecimalOps {

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
   

      def add(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (DoubleDecimal(v), _) => DoubleDecimal(v + y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(v + x.toDouble)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val d = lcm(d1, d2)
          val n = (n1 * d / d1) + (n2 * d / d2)
          FractionDecimal(n, d)
        }
      }
    /*

      case (Constant(Decimal.Infinity), Constant(Decimal.NegInfinity)) =>
      throw new ArithmeticException("Cannot add +inf and -inf")
    case (Constant(Decimal.NegInfinity), Constant(Decimal.Infinity)) =>
      throw new ArithmeticException("Cannot add +inf and -inf")
*/



      def subtract(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (DoubleDecimal(v), _) => DoubleDecimal(v - y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble - v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val d = lcm(d1, d2)
          val n = (n1 * d / d1) - (n2 * d / d2)
          FractionDecimal(n, d)
        }
      }
    
      def multiply(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (DoubleDecimal(v), _) => DoubleDecimal(v * y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble * v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) => {
          val n = n1 * n2
          val d = d1 * d2
          val g = gcd(n, d)
          FractionDecimal(n / g, d / g)
        }
    
      }
    
      /*
            case (NegInfinity, NegInfinity) => Infinity
      case (NegInfinity, Constant(Decimal.Zero)) =>
        throw new ArithmeticException("Cannot multiply -inf by zero")
      case (Infinity, Constant(Decimal.Zero)) =>
        throw new ArithmeticException("Cannot multiply +inf by zero")
      case (Constant(Decimal.Zero), NegInfinity) =>
        throw new ArithmeticException("Cannot multiply -inf by zero")
      case (Constant(Decimal.Zero), Infinity) =>
        throw new ArithmeticException("Cannot multiply +inf by zero")

        */

      def divide(x: Decimal, y: Decimal): Decimal = (x, y) match {
        case (DoubleDecimal(v), _) => DoubleDecimal(v / y.toDouble)
        case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble / v)
        case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) =>
          val n = n1 * d2
          val d = d1 * n2
          val g = gcd(n, d)
          FractionDecimal(n / g, d / g)
      }

      /*
            case (Constant(Decimal.Zero), Constant(Decimal.Zero)) =>
        throw new ArithmeticException("Cannot divide zero by zero")
*/



     /* case Infinity =>
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
      case Constant(Decimal.Zero) =>
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
        }*/


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


          def pow(a: Decimal, b: Decimal): Decimal =
    if (b.isValidInt)
      a.pow(b.toInt)
    else if (a < Decimal.Zero)
      throw new ArithmeticException(s"Undefined: $a ^ $b")
    else
      Decimal(Math.pow(a.toDouble, b.toDouble))

      */
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