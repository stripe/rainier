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

    def or(seq: Seq[Bounds]): Bounds =
        if(seq.forall(_ == PositiveBounds))
            PositiveBounds
        else if (seq.forall(_ == NegativeBounds))
            NegativeBounds
        else
            UnknownBounds

    def sum(seq: Seq[Bounds]): Bounds = or(seq)
    def product(seq: Seq[Bounds]): Bounds =
        if(seq.contains(UnknownBounds))
            UnknownBounds
        else {
            val negativeTerms = seq.count(_ == NegativeBounds) 
            if(negativeTerms % 2 == 0)
                PositiveBounds
            else
                NegativeBounds
        }
            

    def pow(x: Bounds, y: BigDecimal) = {
        if(x == PositiveBounds)
            PositiveBounds
        else if(x == NegativeBounds && y.isValidInt) {
            if(y.toInt % 2 == 0)
                PositiveBounds
            else
                NegativeBounds
        } else
            UnknownBounds
    }

    def pow(x: Bounds) =
        if(x == PositiveBounds)
            PositiveBounds
        else
            UnknownBounds
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