package com.stripe.rainier.core

import com.stripe.rainier.ir.CompiledFunction
import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

/**
  * Generator trait, for posterior predictive distributions to be forwards sampled during sampling
  */
sealed trait Generator[+T] { self =>
  import Generator.{Const, From}

  def requirements: Set[Real]

  def get(implicit r: RNG, n: Evaluator): T

  def map[U](fn: T => U): Generator[U] = self match {
    case Const(reqs, t)     => Const(reqs, fn(t))
    case From(reqs, fromFn) => From(reqs, (r, n) => fn(fromFn(r, n)))
  }

  def flatMap[G, U](fn: T => G)(
      implicit toGen: ToGenerator[G, U]): Generator[U] = self match {
    case Const(reqsL, t) =>
      toGen(fn(t)) match {
        case Const(reqsR, u)     => Const(reqsL ++ reqsR, u)
        case From(reqsR, fromFn) => From(reqsL ++ reqsR, fromFn)
      }
    case From(reqs, fromFn) =>
      From(reqs, { (r, n) =>
        toGen(fn(fromFn(r, n))) match {
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

  private[core] def prepare(parameters: Seq[Parameter])(
      implicit r: RNG): Array[Double] => T = {
    val reqs = requirements.toList.take(Generator.MaxRequirements)
    if (reqs.isEmpty) { array =>
      {
        implicit val evaluator: Evaluator =
          new Evaluator(
            parameters
              .zip(array)
              .toMap)
        get
      }
    } else {
      val namedReqs = reqs.zipWithIndex.map {
        case (r, i) =>
          (s"req$i", r)
      }
      val cf = Compiler.default.compile(parameters.map(_.param), namedReqs)
      array =>
        {
          val globalBuf = new Array[Double](cf.numGlobals)
          val reqValues = new Array[Double](cf.numOutputs)
          0.until(cf.numOutputs).foreach { i =>
            reqValues(i) = CompiledFunction.output(cf, array, globalBuf, i)
          }
          implicit val evaluator: Evaluator =
            new Evaluator(
              (parameters
                .zip(array)
                ++
                  reqs.zip(reqValues)).toMap
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
  val MaxRequirements = 500

  def apply[L, T](l: L)(implicit gen: ToGenerator[L, T]): Generator[T] =
    gen(l)

  case class Const[T](requirements: Set[Real], t: T) extends Generator[T] {
    def get(implicit r: RNG, n: Evaluator): T = t
  }

  case class From[T](requirements: Set[Real], fn: (RNG, Evaluator) => T)
      extends Generator[T] {
    def get(implicit r: RNG, n: Evaluator): T = fn(r, n)
  }

  def constant[T](t: T): Generator[T] = Const(Set.empty, t)

  def from[T](fn: (RNG, Evaluator) => T): Generator[T] = From(Set.empty, fn)

  def real(x: Real): Generator[Double] =
    From(Set(x), { (_, n) =>
      n.toDouble(x)
    })

  def require[T](reqs: Set[Real])(fn: (RNG, Evaluator) => T): Generator[T] =
    From(reqs, fn)

  def vector[T](v: Vector[T]) = from((r, _) => v(r.int(v.size)))

  def categorical[T](pmf: Map[T, Real]): Generator[T] = {
    val cdf =
      pmf.toList
        .scanLeft((Option.empty[T], Real.zero)) {
          case ((_, acc), (t, p)) => ((Some(t)), p + acc)
        }
        .collect { case (Some(t), p) => (t, p) }

    require(cdf.map(_._2).toSet) { (r, n) =>
      val v = r.standardUniform
      cdf.find { case (_, p) => n.toDouble(p) >= v }.getOrElse(cdf.last)._1
    }
  }

  def fromSet[T](items: Set[T]) = vector(items.toVector)

  def traverse[T, U](seq: Seq[T])(
      implicit toGen: ToGenerator[T, U]
  ): Generator[Seq[U]] =
    seq.foldLeft(Generator.constant[Seq[U]](Seq.empty)) { (g, t) =>
      g.zip(toGen(t)).map { case (l, r) => l :+ r }
    }

  def traverse[K, V, W](m: Map[K, V])(
      implicit toGen: ToGenerator[V, W]
  ): Generator[Map[K, W]] =
    m.foldLeft(Generator.constant[Map[K, W]](Map.empty)) {
      case (g, (k, v)) =>
        g.zip(toGen(v)).map { case (m, w) => m.updated(k, w) }
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

  implicit def distribution[T]: ToGenerator[Distribution[T], T] =
    new ToGenerator[Distribution[T], T] {
      def apply(t: Distribution[T]) = t.generator
    }

  implicit val double: ToGenerator[Real, Double] =
    new ToGenerator[Real, Double] {
      def apply(t: Real) = Generator.require[Double](Set(t)) { (_, n) =>
        n.toDouble(t)
      }
    }

  implicit val string: ToGenerator[String, String] =
    new ToGenerator[String, String] {
      def apply(t: String) = Generator.constant(t)
    }

  implicit def zip[A, B, Z, Y](
      implicit az: ToGenerator[A, Z],
      by: ToGenerator[B, Y]): ToGenerator[(A, B), (Z, Y)] =
    new ToGenerator[(A, B), (Z, Y)] {
      def apply(t: (A, B)) = az(t._1).zip(by(t._2))
    }

  implicit def zip3[A, B, C, Z, Y, X](
      implicit az: ToGenerator[A, Z],
      by: ToGenerator[B, Y],
      cx: ToGenerator[C, X]): ToGenerator[(A, B, C), (Z, Y, X)] =
    new ToGenerator[(A, B, C), (Z, Y, X)] {
      def apply(t: (A, B, C)) = az(t._1).zip(by(t._2)).zip(cx(t._3)).map {
        case ((z, y), x) => (z, y, x)
      }
    }

  implicit def zip4[A, B, C, D, Z, Y, X, W](
      implicit az: ToGenerator[A, Z],
      by: ToGenerator[B, Y],
      cx: ToGenerator[C, X],
      dw: ToGenerator[D, W]): ToGenerator[(A, B, C, D), (Z, Y, X, W)] =
    new ToGenerator[(A, B, C, D), (Z, Y, X, W)] {
      def apply(t: (A, B, C, D)) =
        az(t._1).zip(by(t._2)).zip(cx(t._3)).zip(dw(t._4)).map {
          case (((z, y), x), w) => (z, y, x, w)
        }
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

  implicit def vec[T, U](
      implicit tu: ToGenerator[T, U]): ToGenerator[Vec[T], Seq[U]] =
    new ToGenerator[Vec[T], Seq[U]] {
      def apply(t: Vec[T]) =
        Generator.traverse(t.toList.map { x =>
          tu(x)
        })
    }
}
