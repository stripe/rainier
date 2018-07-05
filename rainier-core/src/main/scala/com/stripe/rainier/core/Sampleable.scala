package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG

/**
  * Trait for things which can be sampled
  */
trait Sampleable[-S, +T] {
  def requirements(value: S): Set[Real]
  def get(value: S)(implicit r: RNG, n: Numeric[Real]): T

  def prepare(value: S, context: Context)(
      implicit r: RNG): Array[Double] => T = {
    val reqs = requirements(value).toList
    if (reqs.isEmpty) { array =>
      {
        implicit val evaluator: Evaluator =
          new Evaluator(
            context.variables
              .zip(array)
              .toMap)
        get(value)
      }
    } else {
      val cf = context.compiler.compile(context.variables, reqs)
      array =>
        {
          val reqValues = cf(array)
          implicit val evaluator: Evaluator =
            new Evaluator(
              context.variables
                .zip(array)
                .toMap ++
                reqs.zip(reqValues).toMap
            )
          get(value)
        }
    }
  }
}

/**
  * Things which can be sampled
  */
object Sampleable {
  implicit def generator[T]: Sampleable[Generator[T], T] =
    new Sampleable[Generator[T], T] {
      def requirements(value: Generator[T]): Set[Real] = value.requirements
      def get(value: Generator[T])(implicit r: RNG, n: Numeric[Real]): T =
        value.get
    }

  implicit def placeholder[T, P](
      implicit p: Placeholder[T, P]): Sampleable[P, T] =
    new Sampleable[P, T] {
      def requirements(value: P): Set[Real] = p.requirements(value)
      def get(value: P)(implicit r: RNG, n: Numeric[Real]): T =
        p.get(value)
    }
}
