package rainier.sampler

import rainier.compute._

case class MAP(stepSize: Double) extends Sampler {
  def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    val values = MAP.optimize(density, iterations, stepSize)
    val eval = new Evaluator(values)
    List(Sample(0, true, eval)).iterator
  }
}

object MAP {
  def optimize(density: Real,
               iterations: Int,
               stepSize: Double): Map[Variable, Double] = {
    val cf = Compiler.default.compileGradient(density.variables, density)
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
