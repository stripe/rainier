package com.stripe.rainier.compute

case class Bounds(lower: Double, upper: Double)

object Bounds {
  def apply(value: BigDecimal): Bounds = Bounds(value.toDouble, value.toDouble)

  def or(seq: Seq[Bounds]): Bounds =
    Bounds(seq.map(_.lower).min, seq.map(_.upper).max)

  def sum(seq: Seq[Bounds]): Bounds =
    Bounds(seq.map(_.lower).sum, seq.map(_.upper).sum)

  def multiply(left: Bounds, right: Bounds): Bounds = {
    val options =
      List(left.lower * right.lower,
           left.lower * right.upper,
           left.upper * right.lower,
           left.upper * right.upper)
    Bounds(options.min, options.max)
  }

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

  def reciprocal(x: Bounds): Bounds = {
    val options = List(
      1.0 / x.lower,
      1.0 / x.upper
    )
    Bounds(options.min, options.max)
  }

  def abs(x: Bounds) =
    if (x.lower <= 0.0 && x.upper >= 0.0)
      Bounds(0.0, Math.abs(x.lower).max(x.upper))
    else {
      val options = List(Math.abs(x.lower), Math.abs(x.upper))
      Bounds(options.min, options.max)
    }

  def log(x: Bounds) = Bounds(Math.log(x.lower), Math.log(x.upper))
  def exp(x: Bounds) = Bounds(Math.exp(x.lower), Math.exp(x.upper))
}
