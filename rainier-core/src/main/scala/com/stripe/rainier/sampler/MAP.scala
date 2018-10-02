package com.stripe.rainier.sampler

final case class MAP(stepSize: Double) extends Sampler {
  def sample(densityFunction: DensityFunction,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {
    val values = MAP.optimize(densityFunction, warmupIterations, stepSize)
    List(values)
  }
}

object MAP {
  def optimize(densityFunction: DensityFunction,
               iterations: Int,
               stepSize: Double): Array[Double] = {
    val cf = (params: Array[Double]) =>
      densityFunction.density(params) +: densityFunction.gradient(params)
    val initialValues = 1
      .to(densityFunction.nVars)
      .map { _ =>
        0.0
      }
      .toArray
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
