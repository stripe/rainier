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
    val niterations = 100
    val nsamples = 100
    val stepsize = .1


    def sampleFromGuide(): List[Double] = {
      1.to(K).map { _ =>
        rng.standardNormal
      }
    }


    val guideParams = modelVariables.map(_ => (Unbounded.param, NonNegative.param)) // guide mu and sigma
    val eps: List[RandomVariable[Real]] = guideParams.map {
      case (mu, sigma) => mu + sigma * Normal(0., 1.).param
    }

    val guideDensity = eps.foldLeft(Real(0.)) {case (d, e) => d + e.density}
    // need to change this to density wrt fixed eps.
    // can I just modify the gradients?
    // are gradients/variables ordered in any way?
    val transformedDensity = density
    val surrogateLoss = transformedDensity + guideDensity
    val variables = Real.variables(surrogateLoss).toList
    val guideVariables = Real.variables(guideDensity).toList
    val gradients = Gradient.derive(variables, surrogateLoss)
    val cf = Compiler(surrogateLoss :: gradients)

    val initialValues = guideVariables.flatMap(_ => List(0., 1.))

    val finalValues = 1.to(niterations).foldLeft(initialValues) {
      case (values, _) =>
        val grads = 1.to(nsamples).foldLeft(values) {
          case(grad, _) => {
            val samples = sampleFromGuide()
            val inputs = variables.zip(samples ++ values).toMap
            val outputs = cf(inputs)
            // how to get just the guide gradients?
            values.zip(grad).map {
              case (_, g) => grad + (1. / nsamples) * outputs(g)
            }
          }
        }
        values.zip(grads).map {
          case(v, g) => v + stepsize * g
        }

    }


  }
}
