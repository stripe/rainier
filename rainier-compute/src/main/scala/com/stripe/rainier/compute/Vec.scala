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
  def toList: List[T] =
    0.until(size).toList.map(apply)

  def dot(other: Vec[Real])(implicit ev: T <:< Real): Real =
    Real.sum(0.until(size).map { i =>
      apply(i) * other(i)
    })

  override def toString = {
    val base = s"Vec[$size]"
    val allBounds = toList.map {
      case r: Real => Some(r.bounds)
      case _       => None
    }
    if (allBounds.forall(_.isDefined)) {
      val bounds = Bounds.or(allBounds.map(_.get))
      base + f"(${bounds.lower}%.3g, ${bounds.upper}%.3g)"
    } else
      base
  }
}

object Vec {
  def apply[T,U](seq: T*)(implicit toVec: ToVec[T,U]): Vec[U] =
    from(seq)

  def from[T,U](seq: Seq[T])(implicit toVec: ToVec[T,U]): Vec[U] =
    toVec(seq)
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

trait ToVec[T,U] {
  def apply(seq: Seq[T]): Vec[U]
}

object ToVec {
  implicit def toReal[T](implicit toReal: ToReal[T]): ToVec[T,Real] =
    new ToVec[T,Real] {
      def apply(seq: Seq[T]) = RealVec(seq.map(toReal(_)).toVector)
    }
}