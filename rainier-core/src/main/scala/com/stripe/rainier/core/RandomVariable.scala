package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

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

  def sample[V]()(implicit rng: RNG, sampleable: Sampleable[T, V]): List[V] =
    sample(Sampler.Default.sampler,
           Sampler.Default.warmupIterations,
           Sampler.Default.iterations)

  def sample[V](sampler: Sampler,
                warmupIterations: Int,
                iterations: Int,
                keepEvery: Int = 1)(implicit rng: RNG,
                                    sampleable: Sampleable[T, V]): List[V] =
    toStream(sampler, warmupIterations)
      .take(iterations)
      .sliding(1, keepEvery)
      .map(_.head.apply())
      .toList

  def sampleWithDiagnostics[V](sampler: Sampler,
                               warmupIterations: Int,
                               iterations: Int,
                               chains: Int)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): (List[V], List[Diagnostics]) = {
    val fn = sampleable.prepare(value, density.variables)
    val samples = 1.to(chains).par.map { _ =>
      sampler
        .sample(density, warmupIterations)
        .take(iterations)
        .map { s =>
          (s.parameters, fn(s.parameters))
        }
        .toList
    }
    val allSamples = samples.toList.flatMap { chain =>
      chain.map(_._2)
    }
    val diagnostics = Sampler.diagnostics(samples.toList.map { chain =>
      chain.map(_._1)
    })
    (allSamples, diagnostics)
  }

  def toStream[V](sampler: Sampler, warmupIterations: Int)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): Stream[() => V] = {
    val fn = sampleable.prepare(value, density.variables)
    sampler.sample(density, warmupIterations).map { s =>
      { () =>
        fn(s.parameters)
      }
    }
  }

  val density: Real =
    Real.sum(densities.toList.map(_.toReal))
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
