package rainier.sampler

import rainier.compute._
import rainier.core._

case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  def description: (String, Map[String, Double]) =
    ("Variational",
     Map(
       "Tolerance" -> tolerance,
       "MaxIterations" -> maxIterations.toDouble,
     ))

  def sampleNormal(mean: Double, std: Double)(implicit rng: RNG): Double = {
    mean + std * rng.standardNormal
  }

  override def sample(density: Real, warmUpIterations: Int)(
      implicit rng: RNG): Stream[Sample] = {
    //VariationalOptimizer(tolerance, maxIterations)

    val modelVariables = density.variables

    // use a set of independent normals as the guide
    val K = modelVariables.length
    val niterations = 100
    val nsamples = 100
    val stepsize = .1

    val mus = List.fill(K)(new Variable)
    val sigmas = List.fill(K)(new Variable)
    val epsilonDistribution = Normal(0.0, 1.0)
    val epsilons: List[(Variable, Real)] = List.fill(K) {
      val p = epsilonDistribution.param
      (p.density.variables.head, p.density)
    }

    def sampleFromGuide(): Seq[(Variable, Double)] = {
      epsilons.map { case (v, d) => v -> sampleNormal(0.0, 1.0) }
    }

    val eps
      : List[(Real, RandomVariable[Real])] = (mus zip sigmas zip epsilons) map {
      case ((mu, sigma), (epsilon, _)) => {
        val f = mu + sigma * epsilon
        (f, RandomVariable(f))
      }
    }

    val variablesToEps = (modelVariables zip eps.map(_._1)).toMap

    val guideLogDensity = eps.foldLeft(Real(0.0)) {
      case (d, (_, rv)) => d + rv.density
    }
    // need to change this to density wrt fixed eps.
    // can I just modify the gradients?
    // are gradients/variables ordered in any way?
    val transformedDensity = Real.substituteVariable(density, variablesToEps)
    val surrogateLoss = transformedDensity + guideLogDensity
    val variables = surrogateLoss.variables
//    val guideVariables = guideLogDensity.variables
    val muSigmaVariables = mus ++ sigmas
    val gradientsWithVariables = (surrogateLoss.gradient zip surrogateLoss.variables) filter {
      case (gradient, variable) => muSigmaVariables.contains(variable)
    }
    val (gradients, variablesForCompiling) = gradientsWithVariables.unzip
    val cf = Compiler.default.compile(variables, gradients)

    val initialValues = gradients.flatMap(_ => List(0.0, 1.0))

    def collectMaps[T, U](m: Seq[Map[T, U]]): Map[T, Seq[U]] = {
      m.flatten.groupBy(_._1).mapValues(seqTuples => seqTuples.map(_._2))
    }

    val finalValues = 1.to(niterations).foldLeft(initialValues) {
      case (values, _) =>
        val gradSamples: Seq[Array[Double]] = (1 to nsamples) map { _ =>
          val samples = sampleFromGuide()
          val inputs =
            variables.map((samples ++ (muSigmaVariables zip values)).toMap)
          val outputs = cf(inputs.toArray)
          outputs
        }
        val perDimGradSamples = gradSamples.transpose
        val perDimGrads = perDimGradSamples.map(samples =>
          samples.sum * 1.0 / nsamples.toDouble)
        (values zip perDimGrads).map {
          case (v, g) => v + stepsize * g
        }
    }

    val finalValuesMap = (variables zip finalValues).toMap
    val muValues = mus.map(finalValuesMap)
    val sigmaValues = sigmas.map(finalValuesMap)
    val variationals = muValues zip sigmaValues
    Stream.continually {
      val samples = variationals.map {
        case (mu, sigma) => sampleNormal(mu, sigma)
      }
      val map = (modelVariables zip samples).toMap
      Sample(true, new Evaluator(map))
    }
  }

}
