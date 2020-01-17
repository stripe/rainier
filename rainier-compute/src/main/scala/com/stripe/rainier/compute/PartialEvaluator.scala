package com.stripe.rainier.compute

class PartialEvaluator(var noChange: Set[Real], rowIndex: Int) {
  var cache = Map.empty[Real, Real]

  def next() = new PartialEvaluator(noChange, rowIndex + 1)

  def apply(real: Real): (Real, Boolean) =
    if (noChange.contains(real))
      (real, false)
    else {
      cache.get(real) match {
        case Some(v) => (v, true)
        case None =>
          val (v, changed) = eval(real)
          if (changed)
            cache += real -> v
          else
            noChange += real
          (v, changed)
      }
    }

  private def eval(real: Real): (Real, Boolean) = real match {
    case Scalar(_) => (real, false)
    case c: Column =>
      (c.values(rowIndex), true)
    case l: Line =>
      val terms = l.ax.toList.map { case (x, a) => (apply(x), apply(a)) }
      val (b, bModified) = apply(l.b)
      val anyModified =
        terms.exists { case ((_, m1), (_, m2)) => m1 || m2 } || bModified
      if (anyModified) {
        val sum = Real.sum(terms.map { case ((x, _), (a, _)) => x * a })
        (sum + b, true)
      } else {
        (real, false)
      }
    case l: LogLine =>
      val terms = l.ax.toList.map { case (x, a) => (apply(x), apply(a)) }
      val anyModified =
        terms.exists { case ((_, m1), (_, m2)) => m1 || m2 }
      if (anyModified) {
        val product =
          terms
            .map { case ((x, _), (a, _)) => x.pow(a) }
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
    case Compare(left, right) =>
      val (newLeft, leftModified) = apply(left)
      val (newRight, rightModified) = apply(right)
      if (leftModified || rightModified)
        (RealOps.compare(newLeft, newRight), true)
      else
        (real, false)
    case Pow(base, exponent) =>
      val (newBase, baseModified) = apply(base)
      val (newExponent, exponentModified) = apply(exponent)
      if (baseModified || exponentModified)
        (newBase.pow(newExponent), true)
      else
        (real, false)
    case l: Lookup =>
      val (newIndex, indexModified) = apply(l.index)
      val newTable = l.table.map(apply)
      val anyModified =
        newTable.exists { case (_, modified) => modified }
      if (indexModified || anyModified)
        (Lookup(newIndex, newTable.map(_._1), l.low), true)
      else
        (l, false)
    case p: Parameter =>
      (p, false)
  }
}

object PartialEvaluator {
  def apply(index: Int): PartialEvaluator =
    new PartialEvaluator(Set.empty, index)

  def inline(real: Real, nRows: Int): Real = {
    0.until(nRows)
      .foldLeft((Real.zero, PartialEvaluator(0))) {
        case ((acc, pe), _) =>
          (acc + pe(real)._1, pe.next())
      }
      ._1
  }
}
