package rainier.core

import rainier.compute.Real
import rainier.sampler.RNG

trait Generator[T] { self =>
  def requirements: Set[Real]

  def get(implicit r: RNG, n: Numeric[Real]): T

  def map[U](fn: T => U): Generator[U] = new Generator[U] {
    val requirements = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]) = fn(self.get)
  }

  def flatMap[U](fn: T => Generator[U]): Generator[U] = new Generator[U] {
    val requirements = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]) = fn(self.get).get
  }

  def repeat(k: Int): Generator[Seq[T]] = new Generator[Seq[T]] {
    val requirements = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]) = 0.until(k).map { i =>
      self.get
    }
  }
}

object Generator {
  def apply[T](t: T) = new Generator[T] {
    val requirements = Set.empty
    def get(implicit r: RNG, n: Numeric[Real]) = t
  }

  def from[T](fn: (RNG, Numeric[Real]) => T) =
    new Generator[T] {
      val requirements = Set.empty
      def get(implicit r: RNG, n: Numeric[Real]) = fn(r, n)
    }

  def require[T](reqs: Set[Real])(fn: (RNG, Numeric[Real]) => T) =
    new Generator[T] {
      val requirements = reqs
      def get(implicit r: RNG, n: Numeric[Real]) = fn(r, n)
    }

  def traverse[T](seq: Seq[Generator[T]]) = new Generator[Seq[T]] {
    val requirements = seq.flatMap(_.requirements).toSet
    def get(implicit r: RNG, n: Numeric[Real]) =
      seq.map { g =>
        g.get
      }
  }
}
