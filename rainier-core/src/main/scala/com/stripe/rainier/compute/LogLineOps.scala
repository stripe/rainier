package com.stripe.rainier.compute

import scala.annotation.tailrec

private object LogLineOps {
  def multiply(left: LogLine, right: LogLine): Real = {
    val merged = LineOps.merge(left.ax, right.ax)
    if (merged.isEmpty)
      Real.one
    else
      LogLine(LineOps.merge(left.ax, right.ax))
  }

  def pow(line: LogLine, v: Double): LogLine =
    LogLine(line.ax.map { case (x, a) => x -> a * v })

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  Currently this is just a placeholder - there are no optimizations that
  seem definitely worth doing.
   */
  def log(line: LogLine): Option[Real] = None

  /*
  Factor a scalar constant exponent k out of ax and return it along with
  (a/k)x. We don't want to do this while we're building up the
  computation because we want terms to aggregate and cancel out as much as possible.
  But at the last minute before compilation, this can reduce the total number of
  pow ops needed, or simplify them to optimizable special cases like 2.

  Our current approach for picking k is to find the GCD of the exponents
  if they are all integers; otherwise, give up and just pick 1. (We don't
  want a fractional k since non-integer exponents are presumed to be expensive.)
   */
  def factor(line: LogLine): (LogLine, Double) = {
    val exponents = line.ax.values.toList
    val k =
      if (exponents.forall { x =>
            x % 1 == 0.0
          })
        exponents.map(_.toInt).reduce(gcd)
      else
        1

    (pow(line, 1.0 / k), k.toDouble)
  }

  @tailrec
  private def gcd(x: Int, y: Int): Int = {
    if (y == 0)
      x.abs
    else
      gcd(y, x % y)
  }
}
