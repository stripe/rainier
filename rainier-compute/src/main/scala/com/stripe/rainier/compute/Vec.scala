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
  def ++(other: Vec[Real])(implicit ev: T <:< Real): Vec[Real] =
    ConcatVec(this.map(ev), other)
  def dot(other: Vec[Real])(implicit ev: T <:< Real): Real =
    Real.sum(0.until(size).map { i =>
      apply(i) * other(i)
    })
}

object Vec {
  def apply[T](seq: T*)(implicit toReal: ToReal[T]): Vec[Real] =
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

private case class ConcatVec(left: Vec[Real], right: Vec[Real])
    extends Vec[Real] {
  def size = left.size + right.size
  def apply(index: Int) =
    if (index >= left.size)
      right(index - left.size)
    else left(index)
  def apply(index: Real) =
    Real.gte(index, left.size, right(index - left.size), left(index))
}
