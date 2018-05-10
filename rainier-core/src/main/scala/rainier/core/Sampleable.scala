package rainier.core

import rainier.compute._
import rainier.sampler.RNG

trait Sampleable[-S, +T] {
  def requirements(value: S): Set[Real]
  def get(value: S)(implicit r: RNG, n: Numeric[Real]): T

  def prepare(value: S, variables: Seq[Variable])(
      implicit r: RNG): Array[Double] => T = {
    val reqs = requirements(value).toList
    if (reqs.isEmpty) { array =>
      {
        implicit val evaluator = new Evaluator(variables.zip(array).toMap)
        get(value)
      }
    } else {
      val cf = Compiler.default.compile(variables, reqs)
      array =>
        {
          val reqValues = cf(array)
          implicit val evaluator = new Evaluator(
            variables.zip(array).toMap ++
              reqs.zip(reqValues).toMap
          )
          get(value)
        }
    }
  }
}

object Sampleable {
  implicit def generator[T] = new Sampleable[Generator[T], T] {
    def requirements(value: Generator[T]) = value.requirements
    def get(value: Generator[T])(implicit r: RNG, n: Numeric[Real]) = value.get
  }

  implicit val real = new Sampleable[Real, Double] {
    def requirements(value: Real) = Set(value)
    def get(value: Real)(implicit r: RNG, n: Numeric[Real]) = n.toDouble(value)
  }

  implicit def map[K, S, T](implicit s: Sampleable[S, T]) =
    new Sampleable[Map[K, S], Map[K, T]] {
      def requirements(value: Map[K, S]) =
        value.values.flatMap { v =>
          s.requirements(v)
        }.toSet
      def get(value: Map[K, S])(implicit r: RNG, n: Numeric[Real]): Map[K, T] =
        value.map { case (k, v) => k -> s.get(v) }.toMap
    }

  implicit def zip[A, B, X, Y](implicit ab: Sampleable[A, B],
                               xy: Sampleable[X, Y]) =
    new Sampleable[(A, X), (B, Y)] {
      def requirements(value: (A, X)) =
        ab.requirements(value._1) ++ xy.requirements(value._2)
      def get(value: (A, X))(implicit r: RNG, n: Numeric[Real]): (B, Y) =
        (ab.get(value._1), xy.get(value._2))
    }
}
