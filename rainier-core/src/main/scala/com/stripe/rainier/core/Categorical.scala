package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

/**
  * A finite discrete distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes and values corresponding to the probabilities of those outcomes
  */
final case class Categorical[T](pmf: Map[T, Real]) extends Distribution[T] {
  def map[U](fn: T => U): Categorical[U] =
    flatMap { t =>
      Categorical(Map(fn(t) -> Real.one))
    }

  def flatMap[U](fn: T => Categorical[U]): Categorical[U] =
    Categorical(
      pmf.toList
        .flatMap {
          case (t, p) =>
            fn(t).pmf.toList.map { case (u, p2) => (u, p * p2) }
        }
        .groupBy(_._1)
        .map { case (u, ups) => u -> Real.sum(ups.map(_._2)) }
        .toMap)

  def logDensity(t: T): Real =
    pmf.getOrElse(t, Real.zero).log

  def generator: Generator[T] = {
    val cdf =
      pmf.toList
        .scanLeft((Option.empty[T], Real.zero)) {
          case ((_, acc), (t, p)) => ((Some(t)), p + acc)
        }
        .collect { case (Some(t), p) => (t, p) }

    Generator.require(cdf.map(_._2).toSet) { (r, n) =>
      val v = r.standardUniform
      cdf.find { case (_, p) => n.toDouble(p) >= v }.getOrElse(cdf.last)._1
    }
  }

  def toMixture[V](implicit ev: T <:< Distribution[V]): Mixture[V, T] =
    Mixture[V, T](pmf)

  def toMultinomial: Predictor[Int, Map[T, Int], Multinomial[T]] =
    Predictor.from { k: Int =>
      Multinomial(pmf, k)
    }
}

object Categorical {

  def boolean(p: Real): Categorical[Boolean] =
    Categorical(Map(true -> p, false -> (Real.one - p)))
  def binomial(p: Real): Predictor[Int, Int, Binomial] = Predictor.from {
    k: Int =>
      Binomial(p, k)
  }

  def normalize[T](pmf: Map[T, Real]): Categorical[T] = {
    val total = Real.sum(pmf.values.toList)
    Categorical(pmf.map { case (t, p) => (t, p / total) })
  }

  def list[T](seq: Seq[T]): Categorical[T] =
    normalize(seq.groupBy(identity).mapValues { l =>
      Real(l.size)
    })
}

/**
  * A Multinomial distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes of a single multinomial trial and values corresponding to the probabilities of those outcomes
  * @param k The number of multinomial trials
  */
final case class Multinomial[T](pmf: Map[T, Real], k: Int)
    extends Distribution[Map[T, Int]] {
  def generator: Generator[Map[T, Int]] =
    Categorical(pmf).generator.repeat(k).map { seq =>
      seq.groupBy(identity).map { case (t, ts) => (t, ts.size) }
    }

  def logDensity(t: Map[T, Int]): Real =
    Combinatorics.factorial(k) + Real.sum(t.toList.map {
      case (v, i) =>
        val p = pmf.getOrElse(v, Real.zero)
        val pTerm = if (i == 0 & p == Real.zero) {
          Real.zero
        } else {
          i * p.log
        }
        pTerm - Combinatorics.factorial(i)
    })
}

/**
  * A Binomial distribution with expectation `k*p`
  *
  * @param p The probability of success
  * @param k The number of trials
  */
final case class Binomial(p: Real, k: Int) extends Distribution[Int] {
  val multi: Multinomial[Boolean] =
    Categorical.boolean(p).toMultinomial(k)

  def generator: Generator[Int] = {
    val poissonGenerator = Poisson(p * k).generator.map { _.min(k) }
    val normalGenerator = Normal(k * p, k * p * (1 - p)).generator.map {
      _.toInt.max(0).min(k)
    }
    val binomialGenerator = multi.generator.map { m =>
      m.getOrElse(true, 0)
    }
    Generator.from {
      case (r, n) =>
        val pDouble = n.toDouble(p)
        if (k >= 100 && k * pDouble <= 10) {
          poissonGenerator.get(r, n)
        } else if (k >= 100 && k * pDouble >= 9 && k * (1.0 - pDouble) >= 9) {
          normalGenerator.get(r, n)
        } else { binomialGenerator.get(r, n) }
    }

  }

  def logDensity(t: Int): Real =
    multi.logDensity(Map(true -> t, false -> (k - t)))
}

/**
  * A Mixture distribution
  *
  * @param pmf A Map with keys representing component distributions and values corresponding to the probabilities of those components
  */
final case class Mixture[T, D](pmf: Map[D, Real])(
    implicit ev: D <:< Distribution[T])
    extends Distribution[T] {
  def logDensity(t: T): Real =
    Real
      .sum(pmf.toList.map {
        case (dist, prob) =>
          (ev(dist).logDensity(t) + prob.log).exp
      })
      .log

  def generator: Generator[T] =
    Categorical(pmf).generator.flatMap { d =>
      d.generator
    }
}

/**
  * A Beta-binomial distribution with expectation `a/(a + b) * k`
  *
  */
final case class BetaBinomial(a: Real, b: Real, k: Int)
    extends Distribution[Int] {
  def logDensity(t: Int): Real =
    Combinatorics.choose(k, t) +
      Combinatorics.beta(a + t, k - t + b) -
      Combinatorics.beta(a, b)

  val generator: Generator[Int] =
    Beta(a, b).generator.flatMap { p =>
      Binomial(p, k).generator
    }
}
