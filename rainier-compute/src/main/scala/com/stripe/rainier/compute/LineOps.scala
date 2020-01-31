package com.stripe.rainier.compute

private[compute] object LineOps {

  private def axb(nc: NonConstant): (Coefficients, Constant) =
    nc match {
      case l: Line => (l.ax, l.b)
      case l: LogLine =>
        LogLineOps
          .distribute(l)
          .getOrElse((Coefficients(l), Constant.Zero))
      case _ => (Coefficients(nc), Constant.Zero)
    }

  def sum(left: NonConstant, right: NonConstant): Real = {
    val (lax, lb) = axb(left)
    val (rax, rb) = axb(right)

    val merged = lax.merge(rax)
    if (merged.isEmpty)
      lb + rb
    else
      simplify(merged, lb + rb)
  }

  def scale(nc: NonConstant, v: Constant): Real = {
    val (ax, b) = axb(nc)
    simplify(ax.mapCoefficients(_ * v), b * v)
  }

  def translate(nc: NonConstant, v: Constant): Real = {
    val (ax, b) = axb(nc)
    simplify(ax, b + v)
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
            (x * y, a * c)
        }
    }
    val (newAx, newB) =
      terms.foldLeft((Coefficients.Empty, Constant.Zero)) {
        case ((nAx, nB), (x: NonConstant, a)) =>
          (nAx.merge(Coefficients(x -> a)), nB)
        case ((nAx, nB), (c: Constant, a)) =>
          (nAx, nB + c * a)
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
      case Coefficients.One(x, a) if (a.isPositive) && (line.b.isZero) =>
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
        Some(x.pow(exponent) * RealOps.pow(a, exponent))
      case _ => None
    }

  private def simplify(ax: Coefficients, b: Constant): Real =
    ax match {
      case Coefficients.Empty => b
      case Coefficients.One(x, a) if a.isOne && b.isZero =>
        x
      case _ => Line(ax, b)
    }
}
