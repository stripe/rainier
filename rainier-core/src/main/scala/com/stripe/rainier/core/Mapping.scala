package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.unused

trait Wrapping[T, U] {
  def wrap(t: T): U
  def placeholder(seq: Seq[T]): Placeholder[T, U]
}

object Wrapping {
  implicit def mapping[T, U](implicit m: Mapping[T, U]): Wrapping[T, U] =
    m
}

trait Mapping[T, U] extends Wrapping[T, U] {
  def get(u: U)(implicit n: Numeric[Real]): T
  def requirements(u: U): Set[Real]
}

trait LowPriMappings {
  implicit val int =
    new Mapping[Int, Real] {
      def wrap(t: Int) = Real(t)
      def get(u: Real)(implicit n: Numeric[Real]) =
        n.toDouble(u).toInt
      def requirements(u: Real): Set[Real] = Set(u)
      def placeholder(@unused seq: Seq[Int]) = {
        val x = new Variable
        new Placeholder[Int, Real] {
          val value = x
          val variables = List(x)
          def extract(t: Int, acc: List[Double]) =
            t.toDouble :: acc
        }
      }
    }
}

object Mapping extends LowPriMappings {
  implicit val double =
    new Mapping[Double, Real] {
      def wrap(t: Double) = Real(t)
      def get(u: Real)(implicit n: Numeric[Real]) =
        n.toDouble(u)
      def requirements(u: Real): Set[Real] = Set(u)
      def placeholder(@unused seq: Seq[Double]) = {
        val x = new Variable
        new Placeholder[Double, Real] {
          val value = x
          val variables = List(x)
          def extract(t: Double, acc: List[Double]) =
            t :: acc
        }
      }
    }

  implicit def zip[A, B, X, Y](implicit ab: Mapping[A, B],
                               xy: Mapping[X, Y]): Mapping[(A, X), (B, Y)] =
    new Mapping[(A, X), (B, Y)] {
      def wrap(t: (A, X)) =
        (ab.wrap(t._1), xy.wrap(t._2))
      def get(u: (B, Y))(implicit n: Numeric[Real]) =
        (ab.get(u._1), xy.get(u._2))
      def requirements(u: (B, Y)) =
        ab.requirements(u._1) ++ xy.requirements(u._2)
      def placeholder(seq: Seq[(A, X)]) =
        ab.placeholder(seq.map(_._1)).zip(xy.placeholder(seq.map(_._2)))
    }

  implicit def seq[T, U](implicit tu: Mapping[T, U]): Mapping[Seq[T], Seq[U]] =
    new Mapping[Seq[T], Seq[U]] {
      def wrap(t: Seq[T]): Seq[U] =
        t.map { v =>
          tu.wrap(v)
        }
      def get(u: Seq[U])(implicit n: Numeric[Real]): Seq[T] =
        u.map { x =>
          tu.get(x)
        }
      def requirements(u: Seq[U]): Set[Real] =
        u.flatMap { x =>
          tu.requirements(x)
        }.toSet
      def placeholder(seq: Seq[Seq[T]]) = {
        val maxSize = seq.map(_.size).max
        new Placeholder[Seq[T], Seq[U]] {
          val tuPlaceholders = 0.until(maxSize).map { i =>
            tu.placeholder(seq.flatMap { s =>
              s.lift(i)
            })
          }
          val value = tuPlaceholders.map(_.value)
          val variables = tuPlaceholders.flatMap(_.variables)
          def extract(t: Seq[T], acc: List[Double]) =
            tuPlaceholders.zipWithIndex.foldLeft(acc) {
              case (a, (p, i)) =>
                t.lift(i)
                  .map { v =>
                    p.extract(v, a)
                  }
                  .getOrElse {
                    p.variables.map { _ =>
                      0.0
                    }.toList ++ a
                  }
            }
        }

      }
    }

  implicit def map[K, T, U](
      implicit tu: Mapping[T, U]): Mapping[Map[K, T], Map[K, U]] =
    new Mapping[Map[K, T], Map[K, U]] {
      def wrap(t: Map[K, T]): Map[K, U] =
        t.map { case (k, v) => k -> tu.wrap(v) }
      def get(u: Map[K, U])(implicit n: Numeric[Real]): Map[K, T] =
        u.map { case (k, x) => k -> tu.get(x) }.toMap
      def requirements(u: Map[K, U]): Set[Real] =
        u.values.flatMap { x =>
          tu.requirements(x)
        }.toSet
      def placeholder(seq: Seq[Map[K, T]]) = {
        val keys = seq.foldLeft(Set.empty[K]) {
          case (a, m) => a ++ m.keys.toSet
        }
        new Placeholder[Map[K, T], Map[K, U]] {
          val tuPlaceholders = keys.toList.map { k =>
            k -> tu.placeholder(seq.collect { m =>
              m.get(k) match {
                case Some(t) => t
              }
            })
          }
          val value = tuPlaceholders.map { case (k, p) => k -> p.value }.toMap
          val variables = tuPlaceholders.map(_._2.variables).reduce(_ ++ _)
          def extract(t: Map[K, T], acc: List[Double]) =
            tuPlaceholders.foldLeft(acc) {
              case (a, (k, p)) =>
                t.get(k)
                  .map { v =>
                    p.extract(v, a)
                  }
                  .getOrElse {
                    p.variables.map { _ =>
                      0.0
                    }.toList ++ a
                  }
            }
        }
      }
    }

  def item[T]: Mapping[T, Map[T, Real]] =
    new Mapping[T, Map[T, Real]] {
      def wrap(t: T): Map[T, Real] = Map(t -> Real.one)
      def get(u: Map[T, Real])(implicit n: Numeric[Real]): T =
        u.find { case (_, r) => n.toDouble(r) > 0 }.get._1
      def requirements(u: Map[T, Real]): Set[Real] =
        u.values.toSet
      def placeholder(seq: Seq[T]) = {
        val keys = seq.toSet.toList
        new Placeholder[T, Map[T, Real]] {
          val tPlaceholders = keys.map { k =>
            k -> new Variable
          }
          val value = tPlaceholders.toMap
          val variables = tPlaceholders.map(_._2)
          def extract(t: T, acc: List[Double]) =
            tPlaceholders.foldLeft(acc) {
              case (a, (k, _)) =>
                if (t == k)
                  1.0 :: a
                else
                  0.0 :: a
            }
        }
      }
    }
}
