package rainier.core

import rainier.compute._
import rainier.sampler._

class RandomVariable[+T](private val value: T,
                         private val densities: Set[RandomVariable.BoxedReal]) {

  def flatMap[U](fn: T => RandomVariable[U]): RandomVariable[U] = {
    val rv = fn(value)
    new RandomVariable(rv.value, densities ++ rv.densities)
  }

  def map[U](fn: T => U): RandomVariable[U] =
    new RandomVariable(fn(value), densities)

  def zip[U](other: RandomVariable[U]): RandomVariable[(T, U)] =
    for {
      t <- this
      u <- other
    } yield (t, u)

  def condition(fn: T => Real): RandomVariable[T] =
    for {
      t <- this
      _ <- RandomVariable.fromDensity(fn(t))
    } yield t

  def conditionOn[U](seq: Seq[U])(
      implicit ev: T <:< Likelihood[U]): RandomVariable[T] =
    for {
      t <- this
      _ <- ev(t).fit(seq)
    } yield t

  def get[V](implicit rng: RNG,
             sampleable: Sampleable[T, V],
             num: Numeric[Real]): V =
    sampleable.get(value)(rng, num)

  def sample[V](sampler: Sampler = Sampler.default)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): List[V] =
    sampler.sample(density).toList.map { s =>
      get(rng, sampleable, s.evaluator)
    }

  val density = Real.sum(densities.toList.map(_.toReal))
}

object RandomVariable {

  //this exists to provide a reference-equality wrapper
  //for use in the `densities` set
  private class BoxedReal(val toReal: Real)

  def apply[A](a: A, density: Real): RandomVariable[A] =
    new RandomVariable(a, Set(new BoxedReal(density)))

  def apply[A](a: A): RandomVariable[A] =
    apply(a, Real.zero)

  def fromDensity(density: Real): RandomVariable[Unit] =
    apply((), density)

  def traverse[A](rvs: Seq[RandomVariable[A]]): RandomVariable[Seq[A]] = {

    def go(accum: RandomVariable[Seq[A]], rv: RandomVariable[A]) = {
      for {
        v <- rv
        vs <- accum
      } yield v +: vs
    }

    rvs
      .foldLeft[RandomVariable[Seq[A]]](apply(Seq[A]())) {
        case (accum, elem) => go(accum, elem)
      }
      .map(_.reverse)
  }
}
