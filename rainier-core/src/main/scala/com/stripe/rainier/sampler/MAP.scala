package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

final case class MAP(stepSize: Double) extends Sampler {
  def sample(density: Real,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val values = MAP.optimize(density, warmupIterations, stepSize)
    List(values)
  }
}

object MAP {
  def optimize(density: Real,
               iterations: Int,
               stepSize: Double): Array[Double] = {
    val cf = Compiler.default.compileGradient(density.variables, density)
    val initialValues = density.variables.map { v =>
      0.0
    }.toArray
    1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        val (_, gradients) = cf(values)
        values.zip(gradients).map {
          case (v, g) =>
            v + (stepSize * g)
        }
    }
  }
}
