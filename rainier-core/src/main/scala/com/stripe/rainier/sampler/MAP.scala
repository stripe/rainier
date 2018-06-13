package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class MAP(stepSize: Double) extends Sampler {
  def sample(context: Context,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val values = MAP.optimize(context, warmupIterations, stepSize)
    List(values)
  }
}

object MAP {
  def optimize(context: Context,
               iterations: Int,
               stepSize: Double): Array[Double] = {
    val cf =
      context.compiler
        .compile(context.variables, context.density :: context.gradient)
    val initialValues = context.variables.map { v =>
      0.0
    }.toArray
    1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        val _ :: gradient = cf(values).toList
        values.zip(gradient).map {
          case (v, g) =>
            v + (stepSize * g)
        }
    }
  }
}
