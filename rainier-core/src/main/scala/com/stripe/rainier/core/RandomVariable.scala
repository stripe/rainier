package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
class RandomVariable[+T](val value: T, private val targets: Set[Target]) {

  def flatMap[U](fn: T => RandomVariable[U]): RandomVariable[U] = {
    val rv = fn(value)
    new RandomVariable(rv.value, targets ++ rv.targets)
  }

  def map[U](fn: T => U): RandomVariable[U] =
    new RandomVariable(fn(value), targets)

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
             batches: Int = 1,
             keepEvery: Int = 1)(implicit rng: RNG): Recording = {
    val posteriorParams = Sampler
      .sample(context(batches),
              sampler,
              warmupIterations,
              iterations,
              keepEvery)
    Recording(posteriorParams.map(_.toList))
  }

  def replay[V](recording: Recording)(implicit rng: RNG,
                                      sampleable: Sampleable[T, V]): List[V] = {
    val fn = sampleable.prepare(value, context(1))
    recording.samples.map(fn)
  }

  def replay[V](recording: Recording, iterations: Int)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): List[V] = {
    val fn = sampleable.prepare(value, context(1))
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
                batches: Int = 1,
                keepEvery: Int = 1)(implicit rng: RNG,
                                    sampleable: Sampleable[T, V]): List[V] = {
    val ctx = context(batches)
    val fn = sampleable.prepare(value, ctx)
    Sampler
      .sample(ctx, sampler, warmupIterations, iterations, keepEvery)
      .map { array =>
        fn(array)
      }
  }

  def sampleWithDiagnostics[V](sampler: Sampler,
                               chains: Int,
                               warmupIterations: Int,
                               iterations: Int,
                               parallel: Boolean = true,
                               batches: Int = 1,
                               keepEvery: Int = 1)(
      implicit rng: RNG,
      sampleable: Sampleable[T, V]): (List[V], List[Diagnostics]) = {
    val ctx = context(batches)
    val fn = sampleable.prepare(value, ctx)
    val range = if (parallel) 1.to(chains).par else 1.to(chains)
    val samples =
      range.map { _ =>
        Sampler
          .sample(ctx, sampler, warmupIterations, iterations, keepEvery)
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

  //this is really just here to allow destructuring in for{}
  def withFilter(fn: T => Boolean): RandomVariable[T] =
    if (fn(value))
      this
    else
      RandomVariable(value, Real.zero.log)

  private def context(numBatches: Int): Context = {
    val (base, batched, batches) =
      targets
        .foldLeft((Real.zero, Real.zero, Map.empty[Variable, Array[Double]])) {
          case ((oldBase, oldBatched, oldBatches), target) =>
            val (newBatched, newBatches) =
              target.batch(numBatches)
            if (newBatches.isEmpty)
              (oldBase + newBatched, oldBatched, oldBatches)
            else
              (oldBase, oldBatched + newBatched, oldBatches ++ newBatches)
        }
    new Context(base, batched, batches)
  }

  def density = context(1).density
}

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
object RandomVariable {
  def apply[A](a: A, density: Real): RandomVariable[A] =
    new RandomVariable(a, Set(new Target(density, Map.empty)))

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

private class Target(val toReal: Real,
                     val placeholders: Map[Variable, Array[Double]]) {
  //bad
  def batch(numBatches: Int): (Real, Map[Variable, Array[Double]]) =
    (toReal, placeholders)
}
