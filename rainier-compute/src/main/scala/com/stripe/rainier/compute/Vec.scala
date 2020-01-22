package com.stripe.rainier.compute

sealed trait Vec[T] {
  def size: Int
  def apply(index: Int): T
  def apply(index: Real): T
  def map[U](fn: T => U): Vec[U] = MapVec(this, fn)
  def zip[U](other: Vec[U]): Vec[(T, U)] = {
    require(this.size == other.size)
    ZipVec(this, other)
  }

  override def toString = s"Vec[$size]"
}

object Vec {
  def apply[T](seq: Seq[T])(implicit toReal: ToReal[T]): Vec[Real] =
    RealVec(seq.map(toReal(_)).toVector)
}

private case class RealVec(reals: Vector[Real]) extends Vec[Real] {
  val size = reals.size
  def apply(index: Int) = reals(index)
  def apply(index: Real) = Lookup(index, reals)
}

private case class MapVec[T, U](original: Vec[T], fn: T => U) extends Vec[U] {
  def size = original.size
  def apply(index: Int) = fn(original(index))
  def apply(index: Real) = fn(original(index))
}

private case class ZipVec[T, U](left: Vec[T], right: Vec[U])
    extends Vec[(T, U)] {
  def size = left.size
  def apply(index: Int) = (left(index), right(index))
  def apply(index: Real) = (left(index), right(index))
}
