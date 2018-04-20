package rainier.sampler

import rainier.compute.{Real, Variable}

case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  override def description: (String, Map[String, Double]) = ("Variational",
    Map(
      "Tolerance" -> tolerance,
      "MaxIterations" -> maxIterations.toDouble,
    ))

  override def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    //VariationalOptimizer(tolerance, maxIterations)
    val vars = Real.variables(density)
    val lambdas = vars.map { v =>
            v -> rng.standardNormal
          }.toMap
//    def q(lamb)
    def q(lambdas: Map[Variable, Double])
    def elbo(lambdas: Map[Variable, Double]) = {
      // expectation(density) - expectation(density_variational(lambdas))
      // sum(
    }
  }
}
