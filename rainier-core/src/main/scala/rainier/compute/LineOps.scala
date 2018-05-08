package rainier.compute

private object LineOps {

  def sum(left: Line, right: Line): Real = {
    val merged = merge(left.ax, right.ax)
    if (merged.isEmpty)
      Constant(left.b + right.b)
    else
      Line(merged, left.b + right.b)
  }

  def scale(line: Line, v: Double): Line =
    Line(line.ax.map { case (x, a) => x -> a * v }, line.b * v)

  def translate(line: Line, v: Double): Line =
    Line(line.ax, line.b + v)

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default multiplication behavior.

  If the ax dot-product has only a single term on each side, it's worth trying to expand
  out the multiplication. Otherwise, the combinatorial explosion means the number of terms
  becomes too great.
   */
  def multiply(left: Line, right: Line): Option[Real] =
    if (left.ax.size == 1 && right.ax.size == 1)
      foil(left.ax.head._2,
           left.ax.head._1,
           left.b,
           right.ax.head._2,
           right.ax.head._1,
           right.b)
    else
      None

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term with positive a, we can simplify log(ax) to
  log(a) + log(x). Since we can precompute log(a), this just trades a
  multiply for an add, and there's a chance that log(x) will simplify further.
   */

  def log(line: Line): Option[Real] =
    singleTermOpt(line)
      .filter(_._2 >= 0)
      .map {
        case (x, a) =>
          x.log + Math.log(a)
      }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term, we can simplify ax.pow(k) to
  a.pow(k) * x.pow(k). Since we can precompute a.pow(k), this just moves
  a multiply around, and there's a chance that a.pow(k) will simplify further.
   */
  def pow(line: Line, exponent: Double): Option[Real] =
    singleTermOpt(line).map {
      case (x, a) =>
        x.pow(exponent) * Math.pow(a, exponent)
    }

  private def singleTermOpt(line: Line): Option[(NonConstant, Double)] =
    if (line.ax.size == 1 && line.b == 0)
      Some(line.ax.head)
    else
      None

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
      (scale(line, 1.0 / k), k)
    else
      (line, 1.0)
  }

  def merge(left: Map[NonConstant, Double],
            right: Map[NonConstant, Double]): Map[NonConstant, Double] = {
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

  private def foil(a: Double,
                   x: NonConstant,
                   b: Double,
                   c: Double,
                   y: NonConstant,
                   d: Double): Option[Real] = {
    //(ax + b)(cy + d)
    if (x == y || b == 0.0 || d == 0.0) {
      Some(
        (x * y) * (a * c) + //F
          x * (a * d) + //O
          y * (b * c) + //I
          (b * d)) //L
    } else //too many terms
      None
  }
}
