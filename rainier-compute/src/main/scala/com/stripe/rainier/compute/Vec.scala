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

  private[rainier] def toColumn: T

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
  def apply[T, U](seq: T*)(implicit toVec: ToVec[T, U]): Vec[U] =
    from(seq)

  def from[T, U](seq: Seq[T])(implicit toVec: ToVec[T, U]): Vec[U] =
    toVec(seq)
}

private case class ColumnVec(toColumn: Column) extends Vec[Real] {
  val size = toColumn.values.size
  def apply(index: Int) = Real(toColumn.values(index))
  def apply(index: Real) = Lookup(index, toColumn.values.map(Real(_)))
}

private case class RowVec(reals: Vector[Real]) extends Vec[Real] {
  val size = reals.size
  def apply(index: Int) = reals(index)
  def apply(index: Real) = Lookup(index, reals)
  def toColumn = sys.error("Can not use row vectors in a column context")
}

private case class MapVec[T, U](original: Vec[T], fn: T => U) extends Vec[U] {
  def size = original.size
  def apply(index: Int) = fn(original(index))
  def apply(index: Real) = fn(original(index))
  def toColumn = fn(original.toColumn)
}

private case class ZipVec[T, U](left: Vec[T], right: Vec[U])
    extends Vec[(T, U)] {
  def size = left.size
  def apply(index: Int) = (left(index), right(index))
  def apply(index: Real) = (left(index), right(index))
  def toColumn = (left.toColumn, right.toColumn)
}

trait ToVec[T, U] {
  def apply(seq: Seq[T]): Vec[U]
}

object ToVec {
  implicit val real: ToVec[Real,Real] = 
    new ToVec[Real, Real] {
      def apply(seq: Seq[Real]) = RowVec(seq.toVector)
    }
  
 implicit def zip[A,B,Z,Y](implicit az: ToVec[A,Z], by: ToVec[B,Y]): ToVec[(A,B),(Z,Y)] =
    new ToVec[(A,B),(Z,Y)] {
      def apply(seq: Seq[(A,B)]) = {
        val (a,b) = seq.unzip
        az(a).zip(by(b))
      }
    }
    
  implicit def map[K,T,U](implicit tu: ToVec[T,U]): ToVec[Map[K,T],Map[K,U]] = 
    new ToVec[Map[K,T],Map[K,U]] {
      def apply(seq: Seq[Map[K,T]]) = {
        val keys = seq.foldLeft(Set.empty[K]){case (acc, map) => acc ++ map.keys.toSet}.toList
        ???
      }
    }
}
