package com.stripe.rainier.compute

sealed trait Bounds {
    def strictlyPositive: Boolean
    def strictlyNegative: Boolean
}

object Bounds {
    def any(seq: Seq[Bounds]): Bounds = ???
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