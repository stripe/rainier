package com.stripe.rainier.core

import scala.collection.Map

case class RVG[T](value: RandomVariable[Generator[T]]) {
  def map[U](fn: T => U): RVG[U] = RVG(value.map(_.map(fn)))

  def zip[U](other: RVG[U]): RVG[(T, U)] =
    RVG(value.zip(other.value).map { case (tg, ug) => tg.zip(ug) })

  def flatMapGen[U](fn: T => Generator[U]): RVG[U] =
    RVG(value.map(_.flatMap(fn)))
}

object RVG {
  def traverse[T](seq: Seq[RVG[T]]): RVG[Seq[T]] =
    RVG(
      RandomVariable
        .traverse(seq.map(_.value))
        .map { gs =>
          Generator.traverse(gs)
        })

  def traverse[K, V](map: Map[K, RVG[V]]): RVG[Map[K, V]] =
    RVG(
      RandomVariable
        .traverse(map.map { case (k, v) => k -> v.value })
        .map { gs =>
          Generator.traverse(gs)
        }
    )
}
