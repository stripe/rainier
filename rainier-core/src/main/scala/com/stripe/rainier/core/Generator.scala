package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

/**
  * Generator trait, for posterior predictive distributions to be forwards sampled during sampling
  */
sealed trait Generator[T] { self =>
  import Generator.{Const, From}

  def requirements: Set[Real]

  def get(implicit r: RNG, n: Numeric[Real]): T

  private[core] def withRequirements(newReqs: Set[Real]): Generator[T] =
    self match {
      case Const(reqs, t)     => Const(reqs ++ newReqs, t)
      case From(reqs, fromFn) => From(reqs ++ newReqs, fromFn)
    }

  def map[U](fn: T => U): Generator[U] = self match {
    case Const(reqs, t)     => Const(reqs, fn(t))
    case From(reqs, fromFn) => From(reqs, (r, n) => fn(fromFn(r, n)))
  }

  def flatMap[U](fn: T => Generator[U]): Generator[U] = self match {
    case Const(reqs, t) => fn(t).withRequirements(reqs)
    case From(reqs, fromFn) =>
      From(reqs, { (r, n) =>
        fn(fromFn(r, n)) match {
          case Const(_, u)          => u
          case From(_, innerFromFn) => innerFromFn(r, n)
        }
      })
  }

  def zip[U](other: Generator[U]): Generator[(T, U)] = {
    val reqs: Set[Real] = self.requirements ++ other.requirements
    (self, other) match {
      case (Const(_, t), Const(_, u)) => Const(reqs, (t, u))
      case (From(_, lf), From(_, rf)) =>
        From(reqs, (r, n) => (lf(r, n), rf(r, n)))
      case (Const(_, t), From(_, rf)) => From(reqs, (r, n) => (t, rf(r, n)))
      case (From(_, lf), Const(_, u)) => From(reqs, (r, n) => (lf(r, n), u))
    }
  }

  def repeat(k: Real): Generator[Seq[T]] =
    Generator.require(requirements)(self match {
      case Const(_, u) =>
        (_, n) =>
          Seq.fill(n.toInt(k))(u)
      case From(_, fromFn) =>
        (r, n) =>
          Seq.fill(n.toInt(k))(fromFn(r, n))
    })

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

  case class Const[T](requirements: Set[Real], t: T) extends Generator[T] {
    def get(implicit r: RNG, n: Numeric[Real]): T = t
  }

  case class From[T](requirements: Set[Real], fn: (RNG, Numeric[Real]) => T)
      extends Generator[T] {
    def get(implicit r: RNG, n: Numeric[Real]): T = fn(r, n)
  }

  def constant[T](t: T): Generator[T] = Const(Set.empty, t)

  def from[T](fn: (RNG, Numeric[Real]) => T): Generator[T] = From(Set.empty, fn)

  def real(x: Real): Generator[Double] =
    From(Set(x), { (_, n) =>
      n.toDouble(x)
    })

  def require[T](reqs: Set[Real])(fn: (RNG, Numeric[Real]) => T): Generator[T] =
    From(reqs, fn)

  def traverse[T, U](seq: Seq[T])(
      implicit toGen: ToGenerator[T, U]
  ): Generator[Seq[U]] =
    seq.foldLeft(Generator.constant[Seq[U]](Seq.empty)) { (g, t) =>
      g.zip(toGen(t)).map { case (l, r) => l :+ r }
    }

  def traverse[K, V, W](m: Map[K, V])(
      implicit toGen: ToGenerator[V, W]
  ): Generator[Map[K, W]] = {
    m.foldLeft(Generator.constant[Map[K, W]](Map.empty)) {
      case (g, (k, v)) =>
        g.zip(toGen(v)).map { case (m, w) => m.updated(k, w) }
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
      def apply(t: Real) = Generator.require[Double](Set(t)) { (_, n) =>
        n.toDouble(t)
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
          .traverse(t.toList.map {
            case (k, x) =>
              tu(x).map { v =>
                k -> v
              }
          })
          .map(_.toMap)
    }
}
