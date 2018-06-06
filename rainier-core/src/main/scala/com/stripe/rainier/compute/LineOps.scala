package com.stripe.rainier.compute

private[compute] object LineOps {

  def sum(left: Line, right: Line): Real = {
    val merged = merge(left.ax, right.ax)
    if (merged.isEmpty)
      Constant(left.b + right.b)
    else
      simplify(merged, left.b + right.b)
  }

  def scale(line: Line, v: Double): NonConstant =
    simplify(line.ax.map { case (x, a) => (x, a * v) }, line.b * v)

  def translate(line: Line, v: Double): NonConstant =
    simplify(line.ax, line.b + v)

  /*
  Multiply two lines, using the distribution rule, to produce a new Line.
   */
  def multiply(left: Line, right: Line): Line = {
    val allLeft = (Real.one, left.b) :: left.ax.toList
    val allRight = (Real.one, right.b) :: right.ax.toList
    val terms = allLeft.flatMap {
      case (x, a) =>
        allRight.map {
          case (y, c) =>
            (x * y, a * c)
        }
    }
    val (newAx, newB) = terms.foldLeft((Map.empty[NonConstant, Double], 0.0)) {
      case ((nAx, nB), (x: NonConstant, a)) =>
        (merge(nAx, Map(x -> a)), nB)
      case ((nAx, nB), (Constant(x), a)) =>
        (nAx, nB + x * a)
    }
    Line(newAx, newB)
  }

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
  def pow(line: Line, exponent: Int): Option[Real] =
    line match {
      case Line1(a, x, 0) =>
        Some(x.pow(exponent) * Math.pow(a, exponent.toDouble))
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

    val (k, cnt) = coefficientFreqs.maxBy(_._2)
    if (cnt > coefficientFreqs.getOrElse(1.0, 0))
      (Line(scale(line, 1.0 / k)), k)
    else
      (line, 1.0)
  }

  def merge[N](left: Map[NonConstant, N], right: Map[NonConstant, N])(
      implicit n: Numeric[N]): Map[NonConstant, N] = {
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
            n.plus(bigV, v)
          }
          .getOrElse(v)

        if (newV == n.zero)
          acc - k
        else
          acc + (k -> newV)
    }
  }

  private def simplify(ax: Map[NonConstant, Double], b: Double): NonConstant = {
    if (b == 0.0 && ax.size == 1 && ax.head._2 == 1.0)
      ax.head._1
    else
      Line(ax, b)
  }

  object Line1 {
    def unapply(line: Line): Option[(Double, NonConstant, Double)] = {
      if (line.ax.size == 1)
        Some((line.ax.head._2, line.ax.head._1, line.b))
      else
        None
    }
  }
}
