package com.stripe.rainier.compute

import scala.collection.mutable.ArrayBuffer

trait Encoder[-T] {
  type U
  def wrap(t: T): U
  def create(acc: List[Variable]): (U, List[Variable])
  def extract(t: T, acc: List[Double]): List[Double]
  def columns(ts: Seq[T]): List[Array[Double]] = {
    val first = extract(ts.head, Nil)
    val buffers = first.map { v =>
      ArrayBuffer(v)
    }
    ts.tail.foreach { t =>
      buffers.zip(extract(t, Nil)).foreach {
        case (buf, v) => buf += v
      }
    }
    buffers.map(_.toArray)
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
      def create(acc: List[Variable]) = {
        val u = Real.placeholder()
        (u, u :: acc)
      }
      def extract(t: N, acc: List[Double]) =
        n.toDouble(t) :: acc
    }

  implicit def zip[A, B](implicit a: Encoder[A],
                         b: Encoder[B]): Aux[(A, B), (a.U, b.U)] =
    new Encoder[(A, B)] {
      type U = (a.U, b.U)
      def wrap(t: (A, B)) = (a.wrap(t._1), b.wrap(t._2))
      def create(acc: List[Variable]) = {
        val (bv, acc1) = b.create(acc)
        val (av, acc2) = a.create(acc1)
        ((av, bv), acc2)
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
      def create(acc: List[Variable]) = {
        val (us, vs) =
          1.to(size).foldLeft((List.empty[enc.U], acc)) {
            case ((us, a), _) =>
              val (u, a2) = enc.create(a)
              (u :: us, a2)
          }
        (us.toVector, vs)
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

      def create(acc: List[Variable]): (Map[String, Real], List[Variable]) =
        toMap.fields.foldRight((Map[String, Real](), acc)) {
          case (field, (map, a)) =>
            val v = Real.placeholder()
            (map + (field -> v), v :: a)
        }

      def extract(t: T, acc: List[Double]): List[Double] = {
        val map = toMap.apply(t)
        toMap.fields.foldRight(acc) { case (x, a) => map(x) :: a }
      }
    }
}
