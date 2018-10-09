package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

/**
  * Generator trait, for posterior predictive distributions to be forwards sampled during sampling
  */
trait Generator[T] { self =>
  def requirements: Set[Real]

  def get(implicit r: RNG, n: Numeric[Real]): T

  def map[U](fn: T => U): Generator[U] = new Generator[U] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): U = fn(self.get)
  }

  def flatMap[U](fn: T => Generator[U]): Generator[U] = new Generator[U] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): U = fn(self.get).get
  }

  def zip[U](other: Generator[U]): Generator[(T, U)] = new Generator[(T, U)] {
    val requirements: Set[Real] = self.requirements ++ other.requirements
    def get(implicit r: RNG, n: Numeric[Real]): (T, U) = (self.get, other.get)
  }

  def repeat(k: Real): Generator[Seq[T]] = new Generator[Seq[T]] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): Seq[T] =
      0.until(n.toInt(k)).map { i =>
        self.get
      }
  }

  private[core] def prepare(variables: Seq[Variable])(
      implicit r: RNG): Array[Double] => T = {
    val reqs = requirements.toList
    if (reqs.isEmpty) { array =>
      {
        implicit val evaluator: Evaluator =
          new Evaluator(
            variables
              .zip(array)
              .toMap)
        get
      }
    } else {
      val cf = Compiler.default.compile(variables, reqs)
      array =>
        {
          val reqValues = cf(array)
          implicit val evaluator: Evaluator =
            new Evaluator(
              variables
                .zip(array)
                .toMap ++
                reqs.zip(reqValues).toMap
            )
          get
        }
    }
  }
}

/**
  * Generator object, for posterior predictive distributions to be forwards sampled during sampling
  */
object Generator {
  def apply[T](t: T): Generator[T] = new Generator[T] {
    val requirements: Set[Real] = Set.empty
    def get(implicit r: RNG, n: Numeric[Real]): T = t
  }

  def from[T](fn: (RNG, Numeric[Real]) => T): Generator[T] =
    new Generator[T] {
      val requirements: Set[Real] = Set.empty
      def get(implicit r: RNG, n: Numeric[Real]): T = fn(r, n)
    }

  def real(x: Real): Generator[Double] = new Generator[Double] {
    val requirements: Set[Real] = Set(x)
    def get(implicit r: RNG, n: Numeric[Real]) = n.toDouble(x)
  }

  def require[T](reqs: Set[Real])(fn: (RNG, Numeric[Real]) => T): Generator[T] =
    new Generator[T] {
      val requirements: Set[Real] = reqs
      def get(implicit r: RNG, n: Numeric[Real]): T = fn(r, n)
    }

  def traverse[T](seq: Seq[Generator[T]]): Generator[Seq[T]] =
    new Generator[Seq[T]] {
      val requirements: Set[Real] = seq.flatMap(_.requirements).toSet
      def get(implicit r: RNG, n: Numeric[Real]): Seq[T] =
        seq.map { g =>
          g.get
        }
    }
}

trait ToGenerator[-T, U] {
  def apply(t: T): Generator[U]
}

object ToGenerator {
  implicit def generator[T]: ToGenerator[Generator[T], T] =
    new ToGenerator[Generator[T], T] {
      def apply(t: Generator[T]) = t
    }

  implicit def mapping[T, U](implicit m: Mapping[U, T]): ToGenerator[T, U] =
    new ToGenerator[T, U] {
      def apply(t: T) = new Generator[U] {
        val requirements = m.requirements(t)
        def get(implicit r: RNG, n: Numeric[Real]) = m.get(t)
      }
    }
}
