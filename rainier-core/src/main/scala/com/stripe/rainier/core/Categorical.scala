package com.stripe.rainier.core

import com.stripe.rainier.compute.{If, Real}

/**
  * A finite discrete distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes and values corresponding to the probabilities of those outcomes
  */
final case class Categorical[T](pmf: Map[T, Real])
    extends Distribution[T, Map[T, Real]]()(Placeholder.enum(pmf.keys)) {
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

  //there should be exactly one value set to 1.0
  def logDensity(v: Map[T, Real]): Real =
    Real
      .sum(v.toList.map {
        case (t, r) =>
          (r * pmf.getOrElse(t, Real.zero))
      })
      .log

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

  def toMixture[V, P](implicit ev: T <:< Distribution[V, P],
                      placeholder: Placeholder[V, P]): Mixture[V, P, T] =
    Mixture[V, P, T](pmf)
  def toMultinomial: Predictor[Int, Map[T, Int], Multinomial[T]] =
    Predictor.from { k: Int =>
      Multinomial(pmf, k)
    }
}

object Categorical {

  def boolean(p: Real): Categorical[Boolean] =
    Categorical(Map(true -> p, false -> (Real.one - p)))
  def binomial(p: Real): Predictor[Int, Int, Binomial] =
    Predictor.from { k: Int =>
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
final case class Multinomial[T](pmf: Map[T, Real], k: Real)
    extends Distribution[Map[T, Int], Map[T, Real]] {
  def generator: Generator[Map[T, Int]] =
    Categorical(pmf).generator.repeat(k).map { seq =>
      seq.groupBy(identity).map { case (t, ts) => (t, ts.size) }
    }

  def logDensity(v: Map[T, Real]): Real =
    Combinatorics.factorial(k) + Real.sum(v.toList.map {
      case (t, i) =>
        val p = pmf.getOrElse(t, Real.zero)
        val pTerm =
          If(i, i * p.log, If(p, whenNonZero = i * p.log, whenZero = Real.zero))

        pTerm - Combinatorics.factorial(i)
    })
}

/**
  * A Binomial distribution with expectation `k*p`
  *
  * @param p The probability of success
  * @param k The number of trials
  */
final case class Binomial(p: Real, k: Real) extends Distribution[Int, Real] {
  val multi: Multinomial[Boolean] =
    Multinomial(Map(true -> p, false -> (1 - p)), k)

  def generator: Generator[Int] = {
    val kGenerator = Generator.real(k)
    val poissonGenerator =
      Poisson(p * k).generator
        .zip(kGenerator)
        .map { case (x, kd) => x.min(kd.toInt) }
    val normalGenerator =
      Normal(k * p, k * p * (1 - p)).generator
        .zip(kGenerator)
        .map {
          case (x, kd) => x.toInt.max(0).min(kd.toInt)
        }
    val binomialGenerator = multi.generator.map { m =>
      m.getOrElse(true, 0)
    }

    Generator.from {
      case (r, n) =>
        val pDouble = n.toDouble(p)
        val kDouble = n.toDouble(k)
        if (kDouble >= 100 && kDouble * pDouble <= 10) {
          poissonGenerator.get(r, n)
        } else if (kDouble >= 100 && kDouble * pDouble >= 9 && kDouble * (1.0 - pDouble) >= 9) {
          normalGenerator.get(r, n)
        } else { binomialGenerator.get(r, n) }
    }

  }

  def logDensity(v: Real): Real =
    multi.logDensity(Map(true -> v, false -> (k - v)))
}

/**
  * A Mixture distribution
  *
  * @param pmf A Map with keys representing component distributions and values corresponding to the probabilities of those components
  */
final case class Mixture[T, P, D](pmf: Map[D, Real])(
    implicit ev: D <:< Distribution[T, P],
    placeholder: Placeholder[T, P])
    extends Distribution[T, P] {
  def logDensity(v: P): Real =
    Real
      .sum(pmf.toList.map {
        case (dist, prob) =>
          (ev(dist).logDensity(v) + prob.log).exp
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
final case class BetaBinomial(a: Real, b: Real, k: Real)
    extends Distribution[Int, Real] {
  def logDensity(v: Real): Real =
    Combinatorics.choose(k, v) +
      Combinatorics.beta(a + v, k - v + b) -
      Combinatorics.beta(a, b)

  val generator: Generator[Int] =
    Beta(a, b).generator.flatMap { p =>
      Binomial(p, k).generator
    }
}
