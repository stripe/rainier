package com.stripe.rainier.compute

class PartialEvaluator(var cache: Map[Real, Real]) {
  def apply(real: Real): Real =
    cache.get(real) match {
      case Some(v) => v
      case None => {
        val v = eval(real)
        cache += real -> v
        v
      }
    }

  private def eval(real: Real): Real = real match {
    case Infinity | NegInfinity | Constant(_) => real
    case l: Line =>
      Real.sum(l.ax.map { case (r, d) => apply(r) * Real(d) }) + Real(l.b)
    case l: LogLine =>
      l.ax
        .map { case (r, d) => apply(r).pow(Real(d)) }
        .reduce(_ * _)
    case Unary(original, op) =>
      RealOps.unary(apply(original), op)
    case If(test, nz, z) =>
      If(apply(test), apply(nz), apply(z))
    case Pow(base, exponent) =>
      apply(base).pow(apply(exponent))
    case v: Variable => sys.error(s"No value provided for $v")
  }
}
