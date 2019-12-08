package com.stripe.rainier.compute

import scala.collection.mutable.ArrayBuffer

trait Encoder[-T] {
  type U
  def wrap(t: T): U
  protected def extract(t: T, acc: List[Double]): List[Double]
  protected def create(columns: List[Array[Double]]): (U, List[Array[Double]])

  def encode(ts: Seq[T]): U = {
    val first = extract(ts.head, Nil)
    val buffers = first.map { v =>
      ArrayBuffer(v)
    }
    ts.tail.foreach { t =>
      buffers.zip(extract(t, Nil)).foreach {
        case (buf, v) => buf += v
      }
    }
    create(buffers.map(_.toArray))._1
  }
}

object Encoder {
  type Aux[X, Y] = Encoder[X] { type U = Y }

  implicit def numeric[N](
      implicit n: Numeric[N]
  ): Aux[N, Real] =
    new Encoder[N] {
      type U = Real
      def wrap(t: N) = Real(t)
      def create(columns: List[Array[Double]]) = {
        val u = Real.placeholder(columns.head)
        (u, columns.tail)
      }
      def extract(t: N, acc: List[Double]) =
        n.toDouble(t) :: acc
    }

  implicit def zip[A, B](implicit a: Encoder[A],
                         b: Encoder[B]): Aux[(A, B), (a.U, b.U)] =
    new Encoder[(A, B)] {
      type U = (a.U, b.U)
      def wrap(t: (A, B)) = (a.wrap(t._1), b.wrap(t._2))
      def create(columns: List[Array[Double]]) = {
        val (bv, cols1) = b.create(columns)
        val (av, cols2) = a.create(cols1)
        ((av, bv), cols2)
      }
      def extract(t: (A, B), acc: List[Double]) =
        a.extract(t._1, b.extract(t._2, acc))
    }

  def vector[T](size: Int)(
      implicit enc: Encoder[T]): Aux[Seq[T], IndexedSeq[enc.U]] =
    new Encoder[Seq[T]] {
      type U = IndexedSeq[enc.U]
      def wrap(t: Seq[T]) =
        t.map { x =>
          enc.wrap(x)
        }.toVector
      def create(columns: List[Array[Double]]) = {
        val (us, cols) =
          1.to(size).foldLeft((List.empty[enc.U], columns)) {
            case ((us, col), _) =>
              val (u, col2) = enc.create(col)
              (u :: us, col2)
          }
        (us.toVector, cols)
      }
      def extract(t: Seq[T], acc: List[Double]) =
        t.foldRight(acc) { case (x, a) => enc.extract(x, a) }
    }

  implicit def asMap[T](
      implicit toMap: ToMap[T]): Encoder.Aux[T, Map[String, Real]] =
    new Encoder[T] {
      type U = Map[String, Real]

      def wrap(t: T): Map[String, Real] =
        toMap.apply(t).mapValues(Real(_))

      def create(columns: List[Array[Double]]) =
        toMap.fields.foldRight((Map[String, Real](), columns)) {
          case (field, (map, cols)) =>
            val v = Real.placeholder(cols.head)
            (map + (field -> v), cols.tail)
        }

      def extract(t: T, acc: List[Double]): List[Double] = {
        val map = toMap.apply(t)
        toMap.fields.foldRight(acc) { case (x, a) => map(x) :: a }
      }
    }
}
