package com.stripe.rainier.compute

import Log._

case class Bounds(lower: Double, upper: Double) {
  def isPositive = lower >= 0.0
  def betweenZeroAndOne = isPositive && (upper <= 1.0)
}

object Bounds {
  def or(seq: Seq[Bounds]): Bounds =
    Bounds(seq.map(_.lower).min, seq.map(_.upper).max)

  def sum(seq: Seq[Bounds]): Bounds =
    Bounds(seq.map(_.lower).sum, seq.map(_.upper).sum)

  def multiply(left: Bounds, right: Bounds): Bounds = {
    val options =
      List(multiply(left.lower, right.lower),
           multiply(left.lower, right.upper),
           multiply(left.upper, right.lower),
           multiply(left.upper, right.upper))
    Bounds(options.min, options.max)
  }

  //we want, eg, 0*inf = inf here, to capture the limit as right->inf,
  //since the limit as left->0 will be captured by the other bound
  private def multiply(left: Double, right: Double): Double =
    if (left.isInfinite && right == 0.0)
      left
    else if (left == 0.0 && right.isInfinite)
      right
    else
      left * right

  def pow(x: Bounds, y: Bounds): Bounds = {
    if (y.lower >= 0.0)
      positivePow(x, y)
    else if (y.upper <= 0.0)
      negativePow(x, y)
    else {
      or(
        List(
          negativePow(x, Bounds(y.lower, 0.0)),
          positivePow(x, Bounds(0.0, y.upper))
        ))
    }
  }

  private def positivePow(x: Bounds, y: Bounds): Bounds = {
    if (x.lower >= 0.0)
      positivePositivePow(x, y)
    else if (x.upper <= 0.0)
      negativePositivePow(x, y)
    else {
      or(
        List(
          negativePositivePow(Bounds(x.lower, 0.0), y),
          positivePositivePow(Bounds(0.0, x.upper), y)
        ))
    }
  }

  private def negativePow(x: Bounds, y: Bounds): Bounds =
    reciprocal(positivePow(x, Bounds(y.lower * -1, y.upper * -1)))

  private def positivePositivePow(x: Bounds, y: Bounds): Bounds = {
    val options = List(
      Math.pow(x.lower, y.lower),
      Math.pow(x.lower, y.upper),
      Math.pow(x.upper, y.lower),
      Math.pow(x.upper, y.upper)
    )
    Bounds(options.min, options.max)
  }

  private def negativePositivePow(x: Bounds, y: Bounds): Bounds = {
    if (y.lower == y.upper && y.lower.isValidInt) {
      val options = List(
        Math.pow(x.lower, y.lower),
        Math.pow(x.upper, y.lower)
      )
      Bounds(options.min, options.max)
    } else {
      Bounds(Double.NegativeInfinity, Double.PositiveInfinity)
    }
  }

  def reciprocal(x: Bounds): Bounds =
    if (x.lower <= 0.0 && x.upper >= 0.0)
      Bounds(Double.NegativeInfinity, Double.PositiveInfinity)
    else
      Bounds(1.0 / x.upper, 1.0 / x.lower)

  def abs(x: Bounds) =
    if (x.lower <= 0.0 && x.upper >= 0.0)
      Bounds(0.0, Math.abs(x.lower).max(x.upper))
    else {
      val options = List(Math.abs(x.lower), Math.abs(x.upper))
      Bounds(options.min, options.max)
    }

  def log(x: Bounds) = Bounds(Math.log(x.lower), Math.log(x.upper))
  def exp(x: Bounds) = Bounds(Math.exp(x.lower), Math.exp(x.upper))

  def positive(value: Real)(calc: => Real): Real = {
    if (test(value)(_ >= 0.0))
      calc
    else {
      warn(value, "x >= 0")
      Real.gte(value, Real.zero, calc, Real.negInfinity)
    }
  }

  def zeroToOne(value: Real)(calc: => Real): Real = {
    if (test(value) { v =>
          v >= 0.0 && v <= 1.0
        })
      calc
    else {
      warn(value, "0 <= x <= 1")
      Real.gte(value,
               Real.zero,
               Real.lte(value, Real.one, calc, Real.negInfinity),
               Real.negInfinity)
    }
  }

  def test(value: Real)(fn: Double => Boolean): Boolean =
    fn(value.bounds.lower) && fn(value.bounds.upper)

  def check(value: Real, description: String)(fn: Double => Boolean): Unit =
    if (!test(value)(fn))
      warn(value, description)

  private def warn(value: Real, description: String): Unit =
    WARNING.log("Couldn't prove %s for bounds (%f,%f)",
                description,
                value.bounds.lower,
                value.bounds.upper)
}
