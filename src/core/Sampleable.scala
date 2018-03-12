package rainier.core

import rainier.compute.Real
import rainier.sampler.RNG

trait Sampleable[-S, +T] {
  def get(value: S)(implicit r: RNG, n: Numeric[Real]): T
}

object Sampleable {
  implicit def generator[T] = new Sampleable[Generator[T], T] {
    def get(value: Generator[T])(implicit r: RNG, n: Numeric[Real]) = value.get
  }

  implicit val real = new Sampleable[Real, Double] {
    def get(value: Real)(implicit r: RNG, n: Numeric[Real]) = n.toDouble(value)
  }

  implicit def map[K, S, T](implicit s: Sampleable[S, T]) =
    new Sampleable[Map[K, S], Map[K, T]] {
      def get(value: Map[K, S])(implicit r: RNG, n: Numeric[Real]): Map[K, T] =
        value.map { case (k, v) => k -> s.get(v) }.toMap
    }

  implicit def zip[A, B, X, Y](implicit ab: Sampleable[A, B],
                               xy: Sampleable[X, Y]) =
    new Sampleable[(A, X), (B, Y)] {
      def get(value: (A, X))(implicit r: RNG, n: Numeric[Real]): (B, Y) =
        (ab.get(value._1), xy.get(value._2))
    }
}
