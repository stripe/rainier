package com.stripe.rainier.compute

private object LineOps {

  def sum(left: Line, right: Line): Real = {
    val newLine = Line(merge(left.ax, right.ax), left.b + right.b)
    newLine match {
      case Line1(1.0, x, 0.0) => x
      case _                  => newLine
    }
  }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default multiplication behavior.

  If the ax dot-product has only a single term on each side, it's worth trying to expand
  out the multiplication. Otherwise, the combinatorial explosion means the number of terms
  becomes too great.
   */
  def multiply(left: Line, right: Line): Option[Real] =
    if (left.ax.size * right.ax.size < 10) {
      val axyc =
        left.ax.toList.flatMap {
          case (x, a) =>
            right.ax.toList.map {
              case (y, c) =>
                Map(x * y -> (a * c))
            }
        }
      val axd =
        left.ax.toList.map { case (x, a) => Map(x -> (a * right.b)) }
      val ycb =
        right.ax.toList.map { case (y, c) => Map(y -> (c * left.b)) }
      val bd = left.b * right.b
      val newAx = (axyc ++ axd ++ ycb)
        .reduceOption { (l, r) =>
          merge(l, r)
        }
        .getOrElse(Map.empty)
      val newLine = Line(newAx, bd)
      newLine match {
        case Line1(1.0, x, 0.0) => Some(x)
        case _                  => Some(newLine)
      }
    } else
      None

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term with positive a, we can simplify log(ax) to
  log(a) + log(x). Since we can precompute log(a), this just trades a
  multiply for an add, and there's a chance that log(x) will simplify further.
   */

  def log(line: Line): Option[Real] =
    line match {
      case Line1(a, x, 0) if a >= 0 =>
        Some(x.log + Math.log(a))
      case _ => None
    }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term, we can simplify ax.pow(k) to
  a.pow(k) * x.pow(k). Since we can precompute a.pow(k), this just moves
  a multiply around, and there's a chance that a.pow(k) will simplify further.
   */
  def pow(line: Line, exponent: Double): Option[Real] =
    (line, exponent) match {
      case (Line1(a, x, 0), _) =>
        Some(x.pow(exponent) * Math.pow(a, exponent))
      case (_, 2.0) =>
        multiply(line, line)
      case (_, -2.0) =>
        multiply(line, line).map { x =>
          x.pow(-1)
        }
      case _ => None
    }

  /*
  Factor a scalar constant k out of ax+b and return it along with
  (a/k)x + b/k. We don't want to do this while we're building up the
  computation because we want terms to aggregate and cancel out as much as possible.
  But at the last minute before compilation, this can reduce the total number of
  multiplication ops needed, by reducing some of the weights in ax down to 1 or -1.
  We want to pick the k that maximizes how many get reduced that way.
   */
  def factor(line: Line): (Line, Double) = {
    val coefficientFreqs =
      line.ax.values
        .groupBy(_.abs)
        .map { case (a, xs) => (a, xs.size) }

    if (coefficientFreqs.isEmpty)
      (line, 1.0)
    else {
      val (k, cnt) = coefficientFreqs.maxBy(_._2)
      if (cnt > coefficientFreqs.getOrElse(1.0, 0))
        (Line(line / k), k)
      else
        (line, 1.0)
    }
  }

  def merge(left: Map[Real, Double],
            right: Map[Real, Double]): Map[Real, Double] = {
    val (big, small) =
      if (left.size > right.size)
        (left, right)
      else
        (right, left)

    small.foldLeft(big) {
      case (acc, (k, v)) =>
        val newV = big
          .get(k)
          .map { bigV =>
            bigV + v
          }
          .getOrElse(v)

        if (newV == 0.0)
          acc - k
        else
          acc + (k -> newV)
    }
  }

  object Line1 {
    def unapply(line: Line): Option[(Double, Real, Double)] = {
      if (line.ax.size == 1)
        Some((line.ax.head._2, line.ax.head._1, line.b))
      else
        None
    }
  }
}
