package com.stripe.rainier.compute

import scala.collection.mutable.ArrayBuffer

trait Fn[-A, +Y] { self =>
  protected type X

  protected def wrap(a: A): X
  protected def extract(a: A, acc: List[Double]): List[Double]
  protected def create(columns: List[Array[Double]]): (X, List[Array[Double]])
  protected def xy(x: X): Y

  def apply(a: A): Y = xy(wrap(a))

  def encode(as: Seq[A]): Y = {
    val first = extract(as.head, Nil)
    val buffers = first.map { v =>
      ArrayBuffer(v)
    }
    as.tail.foreach { a =>
      buffers.zip(extract(a, Nil)).foreach {
        case (buf, v) => buf += v
      }
    }
    val x = create(buffers.map(_.toArray))._1
    xy(x)
  }

  def zip[B, Z](fn: Fn[B, Z]): Fn[(A, B), (Y, Z)] =
    new Fn[(A, B), (Y, Z)] {
      type X = (self.X, fn.X)
      def wrap(a: (A, B)) = (self.wrap(a._1), fn.wrap(a._2))
      def create(columns: List[Array[Double]]) = {
        val (av, cols1) = self.create(columns)
        val (bv, cols2) = fn.create(cols1)
        ((av, bv), cols2)
      }
      def extract(a: (A, B), acc: List[Double]) =
        self.extract(a._1, fn.extract(a._2, acc))
      def xy(x: (self.X, fn.X)) = (self.xy(x._1), fn.xy(x._2))
    }

  def map[Z](g: Y => Z): Fn[A, Z] =
    new Fn[A, Z] {
      type X = self.X
      def wrap(a: A) = self.wrap(a)
      def create(columns: List[Array[Double]]) = self.create(columns)
      def extract(a: A, acc: List[Double]) = self.extract(a, acc)

      def xy(x: X) = g(self.xy(x))
    }

  def keys[K](seq: Seq[K]): Fn[Map[K, A], Map[K, Y]] =
    new Fn[Map[K, A], Map[K, Y]] {
      type X = Map[K, self.X]
      def wrap(a: Map[K, A]) = a.map { case (k, v) => k -> self.wrap(v) }
      def create(columns: List[Array[Double]]) = {
        val (pairs, cols2) = seq.foldLeft((List.empty[(K, self.X)], columns)) {
          case ((acc, cols), k) =>
            val (x, cols1) = self.create(cols)
            ((k, x) :: acc, cols1)
        }
        (pairs.toMap, cols2)
      }
      def extract(a: Map[K, A], acc: List[Double]) = seq.reverse.foldLeft(acc) {
        case (acc2, k) =>
          self.extract(a(k), acc2)
      }
      def xy(x: X) = x.map { case (k, v) => k -> self.xy(v) }
    }
}

object Fn {
  def numeric[N](implicit n: Numeric[N]): Fn[N, Real] =
    new Fn[N, Real] {
      type X = Real
      def wrap(a: N) = Real(a)
      def create(columns: List[Array[Double]]) = {
        val x = Real.placeholder(columns.head)
        (x, columns.tail)
      }
      def extract(a: N, acc: List[Double]) =
        n.toDouble(a) :: acc
      def xy(x: Real) = x
    }

  def enum[T](choices: List[T]): Fn[T, List[(T, Real)]] =
    new Fn[T, List[(T, Real)]] {
      type X = List[(T, Real)]
      def wrap(a: T) = choices.map { k =>
        if (a == k) (k, Real.one) else (k, Real.zero)
      }
      def create(columns: List[Array[Double]]) = {
        choices.foldLeft((List.empty[(T, Real)], columns)) {
          case ((acc, cols), k) =>
            ((k, Real.placeholder(cols.head)) :: acc, cols.tail)
        }
      }
      def extract(a: T, acc: List[Double]) =
        choices.reverse.map { k =>
          if (k == a) 1.0 else 0.0
        } ++ acc
      def xy(x: X) = x
    }
}
