package rainier.sampler

import rainier.compute.{Compiler, Gradient, Real, Variable}

case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  override def description: (String, Map[String, Double]) = ("Variational",
    Map(
      "Tolerance" -> tolerance,
      "MaxIterations" -> maxIterations.toDouble,
    ))

  override def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    //VariationalOptimizer(tolerance, maxIterations)
    val vars = Real.variables(density)

    // use a set of independent normals as the guide
    val K = vars.length
    val iterations = 100
    val samples = 100
    val stepsize = .1

    def sampleFromGuide(): List[Real] = {
      1.to(K).map { i =>
        rng.standardNormal
      }
    }
    def transformGuideSamples(guideSamples: List[Real], guideParams: List(Real, Real)): List[Real] = {
      guideSamples.zip(guideParams).map {
        case (eps, (mu, sigma)) =>
          mu + eps * sigma
      }
    }
    // initialize the guide paramaters
    val guideParams = vars.map { v =>
      v -> (rng.standardNormal, abs(rng.standardUniform))
    }.toMap

    val densityGradients = Gradient.derive(guideParams, compose(density, transformGuidesamples)).toList
    val cf = Compiler(compose(density, transformGuidesamples) :: densityGradients)

    val guideGradients = Gradient.derive(guideParams, compose(guide_density, transformGuideSamples)).toList
    val cfGuide = Compiler(compose(guide_density, transformGuidesamples) :: guideGradients)

    // need to optimize the elbo
    // elbo = ELBO≡Expectation_over_guide[density − guide_density]
    // Use the monte carlo estimate of the gradient
    // grad elbo
    // = grad_guide_params Expectation_over_guide[density - guide_density]
    // = Expectation_over_eps[ grad_guide_params density (transformGuideSamples) - grad guide_density (transformGuideSamples) ]
    val finalValues = 1.to(iterations).foldLeft(guideParams) {
      case (values, _) =>
        val inputs = vars.zip(values).toMap
        val outputs = cf(inputs)
        val guideOutputs = cfGuide(inputs)
        values.zip((guideGradients, densityGradients)).map {
          case (v, (guideG, densityG) =>
            // how to get the gradient with respect to guide params at eps?
            v + 1 / samples * 1.to(samples).map(stepSize * (outputs(guideG) - guideOutputs(densityG))).sum()
        }
    }


  }
}
