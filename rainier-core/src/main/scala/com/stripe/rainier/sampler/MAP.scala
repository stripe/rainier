package com.stripe.rainier.sampler

final case class MAP(stepSize: Double) extends Sampler {
  def sample(density: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val values = MAP.optimize(density, warmupIterations, stepSize)
    List(values)
  }
}

object MAP {
  def optimize(density: DensityFunction,
               iterations: Int,
               stepSize: Double): Array[Double] = {
    val cf = (params: Array[Double]) => {
      density.update(params)
      density.gradientVector
    }

    val initialValues = 1
      .to(density.nVars)
      .map { _ =>
        0.0
      }
      .toArray
    1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        val gradient = cf(values).toList
        values.zip(gradient).map {
          case (v, g) =>
            v + (stepSize * g)
        }
    }
  }
}
