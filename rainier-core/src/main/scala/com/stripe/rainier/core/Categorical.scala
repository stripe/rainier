package com.stripe.rainier.core

import com.stripe.rainier.compute.Real

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

final case class Multinomial[T](pmf: Map[T, Real], k: Int)
    extends Distribution[Map[T, Int]] {
  def generator: Generator[Map[T, Int]] =
    Categorical(pmf).generator.repeat(k).map { seq =>
      seq.groupBy(identity).map { case (t, ts) => (t, ts.size) }
    }

  def logDensity(t: Map[T, Int]): Real =
    Combinatrics.factorial(k) + Real.sum(t.toList.map {
      case (v, i) =>
        i * pmf.getOrElse(v, Real.zero).log - Combinatrics.factorial(i)
    })
}

final case class Binomial(p: Real, k: Int) extends Distribution[Int] {
  val multi: Multinomial[Boolean] =
    Categorical.boolean(p).toMultinomial(k)

  def generator: Generator[Int] =
    multi.generator.map { m =>
      m.getOrElse(true, 0)
    }
  def logDensity(t: Int): Real =
    multi.logDensity(Map(true -> t))
}

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
