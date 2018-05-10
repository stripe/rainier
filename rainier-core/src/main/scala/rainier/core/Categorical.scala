package rainier.core

import rainier.compute.Real
import rainier.sampler.RNG

case class Categorical[T](pmf: Map[T, Real]) extends Distribution[T] {
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

  def logDensity(t: T) = pmf.getOrElse(t, Real.zero).log

  def generator: Generator[T] = {
    val cdf =
      pmf.toList
        .scanLeft((Option.empty[T], Real.zero)) {
          case ((_, acc), (t, p)) => ((Some(t)), p + acc)
        }
        .collect { case (Some(t), p) => (t, p) }

    Generator.require(cdf.map(_._2).toSet) { (r, n) =>
      val v = r.standardUniform
      cdf.find { case (t, p) => n.toDouble(p) >= v }.getOrElse(cdf.last)._1
    }
  }

  def toMixture[V](implicit ev: T <:< Distribution[V]) = Mixture[V, T](pmf)
  def toMultinomial = Predictor.from { k: Int =>
    Multinomial(pmf, k)
  }
}

object Categorical {

  def boolean(p: Real) = Categorical(Map(true -> p, false -> (Real.one - p)))
  def binomial(p: Real) = Predictor.from { k: Int =>
    Binomial(p, k)
  }

  def normalize[T](pmf: Map[T, Real]) = {
    val total = Real.sum(pmf.values.toList)
    Categorical(pmf.map { case (t, p) => (t, p / total) })
  }

  def list[T](seq: Seq[T]) =
    normalize(seq.groupBy(identity).mapValues { l =>
      Real(l.size)
    })
}

case class Multinomial[T](pmf: Map[T, Real], k: Int)
    extends Distribution[Map[T, Int]] {
  def generator = Categorical(pmf).generator.repeat(k).map { seq =>
    seq.groupBy(identity).map { case (t, ts) => (t, ts.size) }
  }

  def logDensity(t: Map[T, Int]) =
    Combinatrics.factorial(k) + Real.sum(t.toList.map {
      case (v, i) =>
        i * pmf.getOrElse(v, Real.zero).log - Combinatrics.factorial(i)
    })
}

case class Binomial(p: Real, k: Int) extends Distribution[Int] {
  val multi = Categorical.boolean(p).toMultinomial(k)

  def generator = multi.generator.map { m =>
    m.getOrElse(true, 0)
  }
  def logDensity(t: Int) = multi.logDensity(Map(true -> t))
}

case class Mixture[T, D](pmf: Map[D, Real])(implicit ev: D <:< Distribution[T])
    extends Distribution[T] {
  def logDensity(t: T) =
    Real.logSumExp(pmf.toList.map {
      case (dist, prob) =>
        (ev(dist).logDensity(t) + prob.log)
    })

  def generator = Categorical(pmf).generator.flatMap { d =>
    d.generator
  }
}
