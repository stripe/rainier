package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

/**
  * Generator trait, for posterior predictive distributions to be forwards sampled during sampling
  */
trait Generator[+T] { self =>
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
      0.until(n.toInt(k)).map { _ =>
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
      val namedReqs = reqs.zipWithIndex.map {
        case (r, i) =>
          ("req" + i, r)
      }
      val cf = Compiler.default.compile(variables, namedReqs)
      array =>
        {
          val globalBuf = new Array[Double](cf.numGlobals)
          val reqValues = new Array[Double](cf.numOutputs)
          0.until(cf.numOutputs).foreach { i =>
            reqValues(i) = cf.output(array, globalBuf, i)
          }
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
  def apply[L, T](l: L)(implicit gen: ToGenerator[L, T]): Generator[T] =
    gen(l)

  def constant[T](t: T): Generator[T] =
    new Generator[T] {
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

  def traverse[T, U](t: T)(
      implicit traversal: GeneratorTraversal[T, U]
  ): Generator[U] = traversal(t)
}

trait GeneratorTraversal[-T, U] {
  def apply(t: T): Generator[U]
}

object GeneratorTraversal {
  implicit def seq[V, W](
      implicit toGen: ToGenerator[V, W]
  ): GeneratorTraversal[Seq[V], Seq[W]] =
    new GeneratorTraversal[Seq[V], Seq[W]] {
      def apply(seq: Seq[V]): Generator[Seq[W]] = {
        val asGen = seq.map(toGen(_))
        new Generator[Seq[W]] {
          val requirements: Set[Real] = asGen.flatMap(_.requirements).toSet
          def get(implicit r: RNG, n: Numeric[Real]): Seq[W] = asGen.map(_.get)
        }
      }
    }

  implicit def map[K, V, W](
      implicit toGen: ToGenerator[V, W]
  ): GeneratorTraversal[Map[K, V], Map[K, W]] =
    new GeneratorTraversal[Map[K, V], Map[K, W]] {
      def apply(seq: Map[K, V]): Generator[Map[K, W]] = {
        val asGen = seq.mapValues(toGen(_))
        new Generator[Map[K, W]] {
          val requirements: Set[Real] =
            asGen.values.flatMap(_.requirements).toSet
          def get(implicit r: RNG, n: Numeric[Real]): Map[K, W] =
            asGen.mapValues(_.get)
        }
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

  implicit val double: ToGenerator[Real, Double] =
    new ToGenerator[Real, Double] {
      def apply(t: Real) = new Generator[Double] {
        def get(implicit r: RNG, n: Numeric[Real]): Double =
          n.toDouble(t)
        val requirements = Set(t)
      }
    }

  implicit def zip[A, B, X, Y](
      implicit ab: ToGenerator[A, B],
      xy: ToGenerator[X, Y]): ToGenerator[(A, X), (B, Y)] =
    new ToGenerator[(A, X), (B, Y)] {
      def apply(t: (A, X)) = ab(t._1).zip(xy(t._2))
    }

  implicit def seq[T, U](
      implicit tu: ToGenerator[T, U]): ToGenerator[Seq[T], Seq[U]] =
    new ToGenerator[Seq[T], Seq[U]] {
      def apply(t: Seq[T]) =
        Generator.traverse(t.map { x =>
          tu(x)
        })
    }

  implicit def map[K, T, U](
      implicit tu: ToGenerator[T, U]): ToGenerator[Map[K, T], Map[K, U]] =
    new ToGenerator[Map[K, T], Map[K, U]] {
      def apply(t: Map[K, T]) =
        Generator
          .traverse(t.map {
            case (k, x) =>
              k -> tu(x)
          })
    }
}
