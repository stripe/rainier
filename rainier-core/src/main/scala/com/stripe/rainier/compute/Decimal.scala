package com.stripe.rainier.compute

sealed trait Decimal {
  def ==(other: Decimal) =
    toDouble == other.toDouble
  override def hashCode(): Int =
    toDouble.hashCode()

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

case class DoubleDecimal(toDouble: Double) extends Decimal
case class FractionDecimal(n: Long, d: Long) extends Decimal {
  lazy val toDouble = n.toDouble / d.toDouble
}

object Decimal {
  def apply(value: Double): Decimal = DoubleDecimal(value)
  def apply(value: Int): Decimal = FractionDecimal(value.toLong, 1L)
  def apply(value: Long): Decimal = FractionDecimal(value, 1L)

  val Zero = Decimal(0)
  val One = Decimal(1)
  val Two = Decimal(2)
  val Pi = Decimal(math.Pi)
  val Infinity = Decimal(Double.PositiveInfinity)
  val NegInfinity = Decimal(Double.NegativeInfinity)
}
