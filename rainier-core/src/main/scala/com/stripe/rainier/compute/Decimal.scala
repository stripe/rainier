package com.stripe.rainier.compute

sealed trait Decimal {
  def ==(other: Decimal) =
    this.eq(other) || toDouble == other.toDouble

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
    DecimalOps.abs(this)
  def pow(exponent: Int): Decimal =
    DecimalOps.pow(this, exponent)

  def +(other: Decimal): Decimal =
    DecimalOps.add(this, other)

  def -(other: Decimal): Decimal =
    DecimalOps.subtract(this, other)

  def *(other: Decimal): Decimal =
    DecimalOps.multiply(this, other)

  def /(other: Decimal): Decimal =
    DecimalOps.divide(this, other)
}

object Infinity extends Decimal {
  val toDouble = Double.PositiveInfinity
}
object NegInfinity extends Decimal {
  val toDouble = Double.NegativeInfinity
}

class DoubleDecimal(val toDouble: Double) extends Decimal {
  override lazy val hashCode = toDouble.hashCode
}

class FractionDecimal(val n: Long, val d: Long) extends Decimal {
  lazy val toDouble = n.toDouble / d.toDouble
  override lazy val hashCode = toDouble.hashCode
}

object Decimal {
  def apply(value: Double): Decimal =
    if (value.isPosInfinity)
      Infinity
    else if (value.isNegInfinity)
      NegInfinity
    else if (value.isNaN)
      throw new ArithmeticException("Produced NaN")
    else
      new DoubleDecimal(value)

  def apply(value: Int): Decimal = new FractionDecimal(value.toLong, 1L)
  def apply(value: Long): Decimal = new FractionDecimal(value, 1L)

  val Zero = Decimal(0)
  val One = Decimal(1)
  val Two = Decimal(2)
  val Pi = Decimal(math.Pi)
}
