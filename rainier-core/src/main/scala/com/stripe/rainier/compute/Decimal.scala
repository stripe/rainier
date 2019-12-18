package com.stripe.rainier.compute

import scala.annotation.tailrec

sealed trait Decimal {
  def toDouble: Double
  def toInt: Int = toDouble.toInt

  def isValidInt = toDouble.isValidInt
  def isWhole = toDouble.isWhole

  def >(other: Decimal) =
    toDouble > other.toDouble
  def <(other: Decimal) =
    toDouble < other.toDouble
  def <=(other: Decimal) =
    toDouble <= other.toDouble
  def >=(other: Decimal) =
    toDouble >= other.toDouble

  def abs: Decimal =
    Decimal.abs(this)
  def pow(exponent: Int): Decimal =
    Decimal.pow(this, exponent)

  def +(other: Decimal): Decimal =
    Decimal.add(this, other)

  def -(other: Decimal): Decimal =
    Decimal.subtract(this, other)

  def *(other: Decimal): Decimal =
    Decimal.multiply(this, other)

  def /(other: Decimal): Decimal =
    Decimal.divide(this, other)
}

case class DoubleDecimal(toDouble: Double) extends Decimal
case class FractionDecimal(n: Long, d: Long) extends Decimal {
  lazy val toDouble = n.toDouble / d.toDouble
}

object Decimal {
  def apply(value: Double): Decimal = DoubleDecimal(value)
  def apply(value: Int): Decimal = FractionDecimal(value.toLong, 1L)
  def apply(value: Long): Decimal = FractionDecimal(value, 1L)

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

  def divide(x: Decimal, y: Decimal): Decimal = (x, y) match {
    case (DoubleDecimal(v), _) => DoubleDecimal(v / y.toDouble)
    case (_, DoubleDecimal(v)) => DoubleDecimal(x.toDouble / v)
    case (FractionDecimal(n1, d1), FractionDecimal(n2, d2)) =>
      val n = n1 * d2
      val d = d1 * n2
      val g = gcd(n, d)
      FractionDecimal(n / g, d / g)
  }

  val Zero = Decimal(0)
  val One = Decimal(1)
  val Two = Decimal(2)
  val Pi = Decimal(math.Pi)

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
