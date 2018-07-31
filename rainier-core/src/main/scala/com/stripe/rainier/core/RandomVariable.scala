package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.unused

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
class RandomVariable[+T](val value: T,
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

  def get[V](implicit rng: RNG,
             sampleable: Sampleable[T, V],
             num: Numeric[Real]): V =
    sampleable.get(value)(rng, num)

  def record()(implicit rng: RNG): Recording =
    record(Sampler.Default.iterations)

  def record(iterations: Int)(implicit rng: RNG): Recording =
    record(Sampler.Default.sampler,
           Sampler.Default.warmupIterations,
           iterations)

  def record(sampler: Sampler,
             warmupIterations: Int,
             iterations: Int,
             @unused batches: Int = 1,
             keepEvery: Int = 1)(implicit rng: RNG): Recording = {
    val posteriorParams = Sampler
      .sample(Context(density),
              sampler,
              warmupIterations,
              iterations,
              keepEvery)
    Recording(posteriorParams.map(_.toList))
  }

  def replay[V](recording: Recording)(implicit rng: RNG,
                                      sampleable: Sampleable[T, V]): List[V] = {
    val context = Context(density)
    val fn = sampleable.prepare(value, context)
    recording.samples.map(fn)
  }

  def replay[V](recording: Recording, iterations: Int)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): List[V] = {
    val context = Context(density)
    val fn = sampleable.prepare(value, context)
    val sampledParams = RandomVariable(
      Categorical.list(recording.samples).generator).sample(iterations)
    sampledParams.map(fn)
  }

  def sample[V]()(implicit rng: RNG, sampleable: Sampleable[T, V]): List[V] =
    sample(Sampler.Default.iterations)

  def sample[V](iterations: Int)(implicit rng: RNG,
                                 sampleable: Sampleable[T, V]): List[V] =
    sample(Sampler.Default.sampler,
           Sampler.Default.warmupIterations,
           iterations)

  def sample[V](sampler: Sampler,
                warmupIterations: Int,
                iterations: Int,
                @unused batches: Int = 1,
                keepEvery: Int = 1)(implicit rng: RNG,
                                    sampleable: Sampleable[T, V]): List[V] = {
    val context = Context(density)
    val fn = sampleable.prepare(value, context)
    Sampler
      .sample(context, sampler, warmupIterations, iterations, keepEvery)
      .map { array =>
        fn(array)
      }
  }

  def sampleWithDiagnostics[V](sampler: Sampler,
                               chains: Int,
                               warmupIterations: Int,
                               iterations: Int,
                               parallel: Boolean = true,
                               @unused batches: Int = 1,
                               keepEvery: Int = 1)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): (List[V], List[Diagnostics]) = {
    val context = Context(density)
    val fn = sampleable.prepare(value, context)
    val range = if (parallel) 1.to(chains).par else 1.to(chains)
    val samples =
      range.map { _ =>
        Sampler
          .sample(context, sampler, warmupIterations, iterations, keepEvery)
          .map { array =>
            (array, fn(array))
          }
      }.toList
    val allSamples = samples.flatMap { chain =>
      chain.map(_._2)
    }
    val diagnostics = Sampler.diagnostics(samples.map { chain =>
      chain.map(_._1)
    })
    (allSamples, diagnostics)
  }

  lazy val density: Real =
    Real.sum(densities.toList.map(_.toReal))

  //this is really just here to allow destructuring in for{}
  def withFilter(fn: T => Boolean): RandomVariable[T] =
    if (fn(value))
      this
    else
      RandomVariable(value, Real.zero.log)
}

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
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
