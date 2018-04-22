package rainier.sampler


import rainier.core._
import rainier.compute._


case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  override def description: (String, Map[String, Double]) = ("Variational",
    Map(
      "Tolerance" -> tolerance,
      "MaxIterations" -> maxIterations.toDouble,
    ))

  override def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    //VariationalOptimizer(tolerance, maxIterations)

    val modelVariables = Real.variables(density).toList

    // use a set of independent normals as the guide
    val K = modelVariables.length
    val iterations = 100
    val samples = 100
    val stepsize = .1


    def sampleFromGuide(): List[Double] = {
      1.to(K).map { i =>
        rng.standardNormal
      }
    }


    val guideParams = modelVariables.map(_ => (Unbounded.param, NonNegative.param)) // guide mu and sigma
    val eps: List[RandomVariable[Real]] = guideParams.map {
      case (mu, sigma) => mu + sigma * Normal(0., 1.).param
    }

    val guideDensity = eps.foldLeft(Real(0.)) {case (d, e) => d + e.density}
    val transformedDensity = density // need to change this to density wrt fixed eps
    val surrogateLoss = transformedDensity + guideDensity
    val variables = Real.variables(surrogateLoss).toList
    val guideVariables = Real.variables(guideDensity).toList
    val gradients = Gradient.derive(variables, surrogateLoss)
    val cf = Compiler(surrogateLoss :: gradients)

    val initialValues = guideVariables.map(v => v -> sampleFromGuide())
      .toMap

    val finalValues = 1.to(iterations).foldLeft(initialValues) {
      case (values, _) =>
        // for i in 1 to nsamples
        // set modelVariables to sample from guide with values
        // set variables to modelVariables ++ values
        // grads[i] = cf(variables)
        // update = values + 1/nsamples * sum(grads)

    }


  }
}
