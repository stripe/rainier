package rainier.sampler

import rainier.compute._

case class MAP(iterations: Int, stepSize: Double) extends Sampler {
  val description = ("Gradient MAP",
                     Map(
                       "Iterations" -> iterations.toDouble,
                       "Step Size" -> stepSize
                     ))

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
    val variables = Real.variables(density).toList
    val gradients = Gradient.derive(variables, density).toList
    val cf = Compiler(density :: gradients)
    val initialValues = variables.map { v =>
      0.0
    }
    val finalValues = 1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        val inputs = variables.zip(values).toMap
        val outputs = cf(inputs)
        values.zip(gradients).map {
          case (v, g) =>
            v + (stepSize * outputs(g))
        }
    }
    variables.zip(finalValues).toMap
  }
}
