package com.stripe.rainier.compute

import com.stripe.rainier.unused
import scala.annotation.tailrec

private[compute] object LogLineOps {
  def multiply(left: LogLine, right: LogLine): Real = {
    val merged = left.ax.merge(right.ax)
    if (merged.isEmpty)
      Real.one
    else
      LogLine(merged)
  }

  def pow(line: LogLine, v: Constant): LogLine =
    LogLine(line.ax.mapCoefficients(_ * v))

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  Currently this is just a placeholder - there are no optimizations that
  seem definitely worth doing.
   */
  def log(@unused line: LogLine): Option[Real] = None

  /*
  If possible, return a representation of ax as a list of terms to be summed. Within limits, it's helpful here
  to distribute the multiplications across any internal sums, because that may well surface some terms
  that are in common with whatever we're about to sum this with.

  That is, what we are doing here is:

   * assume we have some expression s = y_0^k_0 * y_1^k_1 * ..., where k_n is constant
   * assume that the y_n terms in s are of themselves of the form a_n_0 * x_n_0 + a_n_1 * x_n_1 + ...
      where a_n_m is constant
   * assume we are now trying to construct s + t for some unknown t
   * we want to restructure s to end up as something like ((a_0_0 * a_1_0 * ...) * (x_0_0 * x_1_0 * ...)) + ...),
      up to some maximum number of additive terms
   * we want to do this because we hope that the non-constant (x_0_0...) parts of these terms may be in
      common with something in t (or some later thing we will add s+t to), and we can combine the constants
   */
  val DistributeToMaxTerms = 20
  def distribute(line: LogLine): Option[(Coefficients, Constant)] = {

    def nTerms(l: Line): Int =
      if (l.b.isZero)
        l.ax.size
      else
        l.ax.size + 1
    def nTerms2(l: Line): Int = {
      val n = nTerms(l)
      (n * (n + 1)) / 2
    }

    val initial = (List.empty[(NonConstant, Constant)], Option.empty[Line])
    val (factors, terms) = line.ax.toList.foldLeft(initial) {
      case ((f, None), (l: Line, c))
          if c.isOne && (nTerms(l) < DistributeToMaxTerms) =>
        (f, Some(l))
      case ((f, Some(t)), (l: Line, c))
          if c.isOne && ((nTerms(t) * nTerms(l)) < DistributeToMaxTerms) =>
        (f, Some(LineOps.multiply(t, l)))
      case ((f, None), (l: Line, c))
          if c.isTwo && (nTerms2(l) < DistributeToMaxTerms) =>
        (f, Some(LineOps.multiply(l, l)))
      case ((f, Some(t)), (l: Line, c))
          if c.isTwo && (nTerms(t) * nTerms2(l) < DistributeToMaxTerms) =>
        (f, Some(LineOps.multiply(t, LineOps.multiply(l, l))))
      case ((f, opt), xa) =>
        (xa :: f, opt)
    }

    terms.map { l =>
      if (factors.isEmpty)
        (l.ax, l.b)
      else {
        val ll = LogLine(Coefficients(factors))
        val (newAx, newB) =
          l.ax.toList.foldLeft((Coefficients(ll -> l.b), Constant.Zero)) {
            case ((nAx, nB), (x, a)) =>
              multiply(ll, LogLine(x)) match {
                case c: Constant => (nAx, nB + c * a)
                case nc: NonConstant =>
                  (nAx.merge(Coefficients(nc -> a)), nB)
              }
          }
        (newAx, newB)
      }
    }
  }

  @tailrec
  private def gcd(x: Int, y: Int): Int = {
    if (y == 0)
      x.abs
    else
      gcd(y, x % y)
  }
}
