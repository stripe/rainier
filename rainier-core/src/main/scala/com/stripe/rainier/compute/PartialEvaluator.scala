package com.stripe.rainier.compute

class PartialEvaluator(var cache: Map[Real, (Real, Boolean)]) {
  def apply(real: Real): (Real, Boolean) =
    cache.get(real) match {
      case Some(pair) => pair
      case None => {
        val pair = eval(real)
        cache += real -> pair
        pair
      }
    }

  private def eval(real: Real): (Real, Boolean) = real match {
    case Infinity | NegInfinity | Constant(_) => (real, false)
    case l: Line =>
      val terms = l.ax.map { case (r, d) => (apply(r), d) }
      val anyModified =
        terms.exists { case ((_, modified), _) => modified }
      if (anyModified) {
        val sum = Real.sum(terms.map { case ((r, _), d) => r * Real(d) })
        (sum + Real(l.b), true)
      } else {
        (real, false)
      }
    case l: LogLine =>
      val terms = l.ax.map { case (r, d) => (apply(r), d) }
      val anyModified =
        terms.exists { case ((_, modified), _) => modified }
      if (anyModified) {
        val product =
          terms
            .map { case ((r, _), d) => r.pow(Real(d)) }
            .reduce { _ * _ }
        (product, true)
      } else {
        (real, false)
      }
    case Unary(original, op) =>
      val (r, modified) = apply(original)
      if (modified)
        (RealOps.unary(r, op), true)
      else
        (real, false)
    case If(test, nz, z) =>
      val (newTest, testModified) = apply(test)
      val (newNz, nzModified) = apply(nz)
      val (newZ, zModified) = apply(z)
      if (testModified || nzModified || zModified)
        (If(newTest, newNz, newZ), true)
      else
        (real, false)
    case Pow(base, exponent) =>
      val (newBase, baseModified) = apply(base)
      val (newExponent, exponentModified) = apply(exponent)
      if (baseModified || exponentModified)
        (newBase.pow(newExponent), true)
      else
        (real, false)
    case v: Variable =>
      (v, false)
  }
}

object PartialEvaluator {
  def from(map: Map[Variable, Real]): PartialEvaluator =
    new PartialEvaluator(map.map { case (k, v) => (k, (v, true)) })
  def inline(real: Real, map: Map[Variable, Real]): Real =
    from(map).apply(real)._1
}
