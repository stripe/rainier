package com.stripe.rainier.compute

private[compute] object LineOps {

  private def axb(nc: NonConstant): (Coefficients, Constant) =
    nc match {
      case l: Line => (l.ax, l.b)
      case l: LogLine =>
        LogLineOps
          .distribute(l)
          .getOrElse((Coefficients.one(l), Real.constZero))
      case _ => (Coefficients.one(nc), Real.constZero)
    }

  def sum(left: NonConstant, right: NonConstant): Real = {
    val (lax, lb) = axb(left)
    val (rax, rb) = axb(right)

    val merged = lax.merge(rax)
    if (merged.isEmpty)
      lb + rb
    else
      simplify(merged, RealOps.addC(lb, rb))
  }

  def scale(nc: NonConstant, v: Constant): Real = {
    val (ax, b) = axb(nc)
    simplify(ax.mapCoefficients{a => RealOps.mulC(a, v)}, RealOps.mulC(b, v))
  }

  def translate(nc: NonConstant, v: Constant): Real = {
    val (ax, b) = axb(nc)
    simplify(ax, RealOps.addC(b, v))
  }

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
            (x * y, RealOps.mulC(a, c))
        }
    }
    val (newAx, newB) =
      terms.foldLeft((Coefficients.Empty, Real.constZero)) {
        case ((nAx, nB), (x: NonConstant, a)) =>
          (nAx.merge(Coefficients(x -> a)), nB)
        case ((nAx, nB), (x: Constant, a)) =>
          (nAx, RealOps.addC(nB, RealOps.mulC(x, a)))
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
    line.ax match {
      case Coefficients.One(x, a)
          if a.bounds.isPositive && (line.b.isZero) =>
        Some(x.log + a.log)
      case _ => None
    }

  /*
  Return Some(real) if an optimization is possible here,
  otherwise None will fall back to the default log behavior.

  If this line is just a single a*x term, we can simplify ax.pow(k) to
  a.pow(k) * x.pow(k). Since we can precompute a.pow(k), this just moves
  a multiply around, and there's a chance that a.pow(k) will simplify further.
   */
  def pow(line: Line, exponent: Constant): Option[Real] =
    line.ax match {
      case Coefficients.One(x, a) if line.b.isZero =>
        Some(x.pow(exponent) * RealOps.powC(a, exponent))
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
  def factor(line: Line): (Coefficients, Constant, Constant) = {
    val coefficientFreqs =
      line.ax.coefficients
        .groupBy{a => RealOps.absC(a)}
        .map { case (a, xs) => (a, xs.size) }

    val (k, cnt) = coefficientFreqs.maxBy(_._2)
    if (cnt > coefficientFreqs.getOrElse(Real.constOne, 0))
      (line.ax.mapCoefficients{a => RealOps.divC(a, k)}, RealOps.divC(line.b, k), k)
    else
      (line.ax, line.b, Real.constOne)
  }

  private def simplify(ax: Coefficients, b: Constant): Real =
    ax match {
      case Coefficients.Empty => b
      case Coefficients.One(x, Real.one) if b.isZero =>
        x
      case _ => Line(ax, b)
    }
}
