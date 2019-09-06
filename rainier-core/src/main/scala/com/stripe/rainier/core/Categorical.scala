package com.stripe.rainier
package core

import com.stripe.rainier.compute._

/**
  * A finite discrete distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes and values corresponding to the probabilities of those outcomes
  */
final case class Categorical[T](pmf: Map[T, Real]) extends Distribution[T] {
  self =>

  def map[U](fn: T => U): Categorical[U] =
    Categorical(
      pmf.foldLeft(Map.empty[U, Real]) {
        case (acc, (t, p)) =>
          updateMap(acc, fn(t), p)(Real.zero)(_ + _)
      }
    )

  def flatMap[U](fn: T => Categorical[U]): Categorical[U] =
    Categorical(
      (for {
        (t, p) <- pmf.iterator
        (u, p2) <- fn(t).pmf.iterator
      } yield (u, p * p2)).foldLeft(Map.empty[U, Real]) {
        case (acc, (u, p)) =>
          updateMap(acc, u, p)(Real.zero)(_ + _)
      }
    )

  def zip[U](other: Categorical[U]): Categorical[(T, U)] =
    Categorical(
      for {
        (t, p) <- pmf
        (u, p2) <- other.pmf
      } yield ((t, u), p * p2)
    )

  def generator: Generator[T] = {
    val cdf =
      pmf.toList
        .scanLeft((Option.empty[T], Real.zero)) {
          case ((_, acc), (t, p)) => ((Some(t)), p + acc)
        }
        .collect { case (Some(t), p) => (t, p) }

    Generator.require(cdf.map(_._2).toSet) { (r, n) =>
      val v = r.standardUniform
      require(Math.abs(n.toDouble(cdf.last._2) - 1.0) < 1e-6)
      cdf.find { case (_, p) => n.toDouble(p) >= v }.getOrElse(cdf.last)._1
    }
  }

  def toMixture[V](implicit ev: T <:< Continuous): Mixture =
    Mixture(pmf.map { case (k, v) => (ev(k), v) })

  def toMultinomial = Predictor[Int].from { i =>
    Multinomial(pmf, i)
  }

  def likelihood =
    new Likelihood[T] {
      val choices = pmf.keys.toList
      val u = choices.map { k =>
        k -> new Variable
      }
      val real = Categorical.logDensity(self, u)
      val placeholders = u.map(_._2)

      def extract(t: T) =
        choices.map { k =>
          if (t == k)
            1.0
          else
            0.0
        }
    }
}

object Categorical {
  def logDensity[T](cat: Categorical[T], value: List[(T, Real)]) =
    Real
      .sum(value.map {
        case (t, r) =>
          (r * cat.pmf.getOrElse(t, Real.zero))
      })
      .log

  def boolean(p: Real): Categorical[Boolean] =
    Categorical(Map(true -> p, false -> (Real.one - p)))

  def binomial(p: Real) = Predictor[Int].from(Binomial(p, _))

  def normalize[T](pmf: Map[T, Real]): Categorical[T] = {
    val total = Real.sum(pmf.values.toList)
    Categorical(pmf.map { case (t, p) => (t, p / total) })
  }

  def list[T](seq: Seq[T]): Categorical[T] =
    normalize(seq.groupBy(identity).mapValues { l =>
      Real(l.size)
    })

  def fromSet[T](ts: Set[T]): Categorical[T] = {
    val p = Real.one / ts.size
    Categorical(
      ts.foldLeft(Map.empty[T, Real])((m, t) => m.updated(t, p))
    )
  }

  implicit def gen[T]: ToGenerator[Categorical[T], T] =
    new ToGenerator[Categorical[T], T] {
      def apply(c: Categorical[T]) = c.generator
    }
}

/**
  * A Multinomial distribution
  *
  * @param pmf A map with keys corresponding to the possible outcomes of a single multinomial trial and values corresponding to the probabilities of those outcomes
  * @param k The number of multinomial trials
  */
final case class Multinomial[T](pmf: Map[T, Real], k: Real)
    extends Distribution[Map[T, Int]] { self =>
  def generator: Generator[Map[T, Int]] =
    Categorical(pmf).generator.repeat(k).map { seq =>
      seq.groupBy(identity).map { case (t, ts) => (t, ts.size) }
    }

  def likelihood =
    new Likelihood[Map[T, Int]] {
      val choices = pmf.keys.toList
      val u = choices.map { k =>
        k -> new Variable
      }
      val real = Multinomial.logDensity(self, u)
      val placeholders = u.map(_._2)

      def extract(t: Map[T, Int]) =
        choices.map { k =>
          t.getOrElse(k, 0).toDouble
        }
    }
}

object Multinomial {
  def optional[T](pmf: Map[T, Real], k: Real): Multinomial[Option[T]] = {
    val total = Real.sum(pmf.values)
    val newPMF = pmf.map { case (t, p) => Option(t) -> p } + (None -> (Real.one - total))
    Multinomial(newPMF, k)
  }

  def logDensity[T](multi: Multinomial[T], v: List[(T, Real)]): Real =
    Combinatorics.factorial(multi.k) + Real.sum(v.map {
      case (t, i) =>
        val p = multi.pmf.getOrElse(t, Real.zero)
        val pTerm =
          Real.eq(i, Real.zero, Real.zero, i * p.log)
        pTerm - Combinatorics.factorial(i)
    })

  implicit def gen[T]: ToGenerator[Multinomial[T], Map[T, Int]] =
    new ToGenerator[Multinomial[T], Map[T, Int]] {
      def apply(m: Multinomial[T]) = m.generator
    }
}
