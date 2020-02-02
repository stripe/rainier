package com.stripe.rainier.compute

sealed trait Vec[T] {
  def size: Int
  def apply(index: Int): T
  def apply(index: Real): T

  def take(k: Int): Vec[T] = mapLeaves { r =>
    RealVec(r.reals.take(k))
  }
  def drop(k: Int): Vec[T] = mapLeaves { r =>
    RealVec(r.reals.drop(k))
  }

  private[compute] def mapLeaves(g: RealVec => RealVec): Vec[T]

  def map[U](fn: T => U): Vec[U] = MapVec(this, fn)
  def zip[U](other: Vec[U]): Vec[(T, U)] = {
    require(this.size == other.size)
    ZipVec(this, other)
  }

  def toList: List[T] =
    0.until(size).toList.map(apply)

  def toColumn: T =
    apply(new Column(0.until(size).map(_.toDouble).toArray))

  def dot(other: Vec[Real])(implicit ev: T <:< Real): Real =
    Real.sum(0.until(size).map { i =>
      apply(i) * other(i)
    })

  override def toString = "Vec" + toList.toString.drop(4)
}

object Vec {
  def apply[T, U](seq: T*)(implicit toVec: ToVec[T, U]): Vec[U] =
    from(seq)

  def from[T, U](seq: Seq[T])(implicit toVec: ToVec[T, U]): Vec[U] =
    toVec(seq)
}

private case class RealVec(reals: Vector[Real]) extends Vec[Real] {
  val size = reals.size
  def apply(index: Int) = reals(index)
  def apply(index: Real) = Lookup(index, reals)
  def mapLeaves(g: RealVec => RealVec) = g(this)
}

private case class MapVec[T, U](original: Vec[T], fn: T => U) extends Vec[U] {
  def size = original.size
  def apply(index: Int) = fn(original(index))
  def apply(index: Real) = fn(original(index))
  def mapLeaves(g: RealVec => RealVec) =
    MapVec(original.mapLeaves(g), fn)
}

private case class ZipVec[T, U](left: Vec[T], right: Vec[U])
    extends Vec[(T, U)] {
  def size = left.size
  def apply(index: Int) = (left(index), right(index))
  def apply(index: Real) = (left(index), right(index))
  def mapLeaves(g: RealVec => RealVec) =
    ZipVec(left.mapLeaves(g), right.mapLeaves(g))
}

private case class TraverseVec[T](list: List[Vec[T]]) extends Vec[List[T]] {
  val size = list.head.size
  require(list.forall(_.size == size))

  def apply(index: Int) = list.map(_.apply(index))
  def apply(index: Real) = list.map(_.apply(index))
  def mapLeaves(g: RealVec => RealVec) =
    TraverseVec(list.map { v =>
      v.mapLeaves(g)
    })
}

trait ToVec[T, U] {
  def apply(seq: Seq[T]): Vec[U]
}

object ToVec {
  implicit def toReal[T](implicit toReal: ToReal[T]): ToVec[T, Real] =
    new ToVec[T, Real] {
      def apply(seq: Seq[T]) = RealVec(seq.map(toReal(_)).toVector)
    }

  implicit def zip[A, B, Z, Y](implicit az: ToVec[A, Z],
                               by: ToVec[B, Y]): ToVec[(A, B), (Z, Y)] =
    new ToVec[(A, B), (Z, Y)] {
      def apply(seq: Seq[(A, B)]) = {
        val (a, b) = seq.unzip
        az(a).zip(by(b))
      }
    }

  implicit def map[K, T, U](
      implicit tu: ToVec[T, U]): ToVec[Map[K, T], Map[K, U]] =
    new ToVec[Map[K, T], Map[K, U]] {
      def apply(seq: Seq[Map[K, T]]) = {
        val keys = seq.head.keys.toList
        val valueVecs = keys.map { k =>
          tu(seq.map { m =>
            m(k)
          })
        }
        TraverseVec(valueVecs).map { us =>
          keys.zip(us).toMap
        }
      }
    }

  implicit def list[T, U](implicit tu: ToVec[T, U],
                          uu: ToVec[U, U]): ToVec[List[T], Vec[U]] =
    new ToVec[List[T], Vec[U]] {
      def apply(seq: Seq[List[T]]) = {
        val size = seq.head.size
        val valueVecs = 0.until(size).toList.map { k =>
          tu(seq.map { m =>
            m(k)
          })
        }
        TraverseVec(valueVecs).map { s =>
          Vec.from(s)
        }
      }
    }
}
