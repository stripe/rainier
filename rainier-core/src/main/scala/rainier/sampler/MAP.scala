package rainier.sampler

import rainier.compute._
import rainier.compute.compiler._

case class MAP(stepSize: Double) extends Sampler {
  def sample(density: Real, warmupIterations: Int)(
      implicit rng: RNG): Stream[Sample] = {
    val values = MAP.optimize(density, warmupIterations, stepSize)
    val eval = new Evaluator(values)
    Stream.continually(Sample(true, eval))
  }
}

object MAP {
  def optimize(density: Real,
               iterations: Int,
               stepSize: Double): Map[Variable, Double] = {
    val cf = Compiler.compileGradient(density.variables, density)
    val initialValues = density.variables.map { v =>
      0.0
    }.toArray
    val finalValues = 1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        val (_, gradients) = cf(values)
        values.zip(gradients).map {
          case (v, g) =>
            v + (stepSize * g)
        }
    }
    density.variables.zip(finalValues).toMap
  }
}
