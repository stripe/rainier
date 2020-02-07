package com.stripe.rainier.core

import com.stripe.rainier.compute._

/**
  * A Multinomial distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes of a single multinomial trial and values corresponding to the probabilities of those outcomes
  * @param k The number of multinomial trials
  */
final case class Multinomial[T](pmf: Map[T, Real], k: Real)
    extends Distribution[Map[T, Long]] { self =>

  def logDensity(seq: Seq[Map[T, Long]]) =
    Vec.from(seq).map(logDensity).columnize

  def logDensity(v: Map[T, Real]): Real =
    Combinatorics.factorial(k) + Real.sum(v.map {
      case (t, i) =>
        val p = pmf.getOrElse(t, Real.zero)
        val pTerm =
          Real.eq(i, Real.zero, Real.zero, i * p.log)
        pTerm - Combinatorics.factorial(i)
    })

  def generator: Generator[Map[T, Long]] =
    Generator.categorical(pmf).repeat(k).map { seq =>
      seq.groupBy(identity).map { case (t, ts) => (t, ts.size.toLong) }
    }
}

object Multinomial {
  def optional[T](pmf: Map[T, Real], k: Real): Multinomial[Option[T]] = {
    val total = Real.sum(pmf.values)
    val newPMF = pmf.map { case (t, p) => Option(t) -> p } + (None -> (Real.one - total))
    Multinomial(newPMF, k)
  }
}
