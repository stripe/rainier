package com.stripe.rainier.compute

case class Decimal(value: BigDecimal) {
    def toDouble = value.toDouble
    def toInt = value.toInt

    def isValidInt = value.isValidInt
    def isWhole = value.isWhole

    def abs = Decimal(value.abs)

    def pow(exponent: Int) =
        Decimal(value.pow(exponent))
        
    def >(other: Decimal) =
        value > other.value
    def <(other: Decimal) =
        value < other.value
    def <=(other: Decimal) =
        value <= other.value
    def >=(other: Decimal) =
        value >= other.value

    def +(other: Decimal) =
        Decimal(value + other.value)

    def -(other: Decimal) =
        Decimal(value - other.value)

    def *(other: Decimal) =
        Decimal(value * other.value)

    def /(other: Decimal) =
        Decimal(value / other.value)
}

object Decimal {
    def apply(value: Double): Decimal =
        Decimal(BigDecimal(value))
    def apply(value: Int): Decimal =
        Decimal(value.toDouble)

    val Zero = Decimal(0.0)
    val One = Decimal(1.0)
    val Two = Decimal(2.0)
    val Pi = Decimal(math.Pi)  
}