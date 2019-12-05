package com.stripe.rainier.compute

sealed trait Bounds {
    def strictlyPositive: Boolean
    def strictlyNegative: Boolean
}

object Bounds {
    def apply(value: BigDecimal): Bounds = {
        if(value > 0.0)
            PositiveBounds
        else if(value < 0.0)
            NegativeBounds
        else
            UnknownBounds
    }

    def any(seq: Seq[Bounds]): Bounds = ???
    def sum(seq: Seq[Bounds]): Bounds = ???
    def multiply(left: Bounds, right: Bounds): Bounds = ???
}

object UnknownBounds extends Bounds {
    val strictlyPositive = false
    val strictlyNegative = false
}

object PositiveBounds extends Bounds {
    val strictlyPositive = true
    val strictlyNegative = false
}

object NegativeBounds extends Bounds {
    val strictlyPositive = false
    val strictlyNegative = true
}