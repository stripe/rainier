package rainier.sampler

import rainier.core._
import rainier.compute._

case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  override def description: (String, Map[String, Double]) =
    ("Variational",
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

    def sampleFromGuide(): Seq[Double] = {
      1.to(K).map { _ =>
        rng.standardNormal
      }
    }

    val mus = List.fill(K)(Unbounded.param)
    val sigmas = List.fill(K)(NonNegative.param)
    val epsilons = List.fill(K)(Normal(0.0, 1.0).param)

    val eps: List[RandomVariable[Real]] = (mus zip sigmas zip epsilons) map {
      case ((mu, sigma), epsilon) =>
        for {
          muS <- mu
          sigmaS <- sigma
          epsS <- epsilon
        } yield muS + sigmaS * epsS
    }

    val guideLogDensity = eps.foldLeft(Real(0.0)) {
      case (d, e) => d + e.density
    }
    // need to change this to density wrt fixed eps.
    // can I just modify the gradients?
    // are gradients/variables ordered in any way?
    val transformedDensity = density
    val surrogateLoss = transformedDensity + guideLogDensity
    val variables = Real.variables(surrogateLoss).toList
    val guideVariables = Real.variables(guideLogDensity).toList
    val gradients = Gradient.derive(variables, surrogateLoss)
    val cf = Compiler(gradients)

    val initialValues = guideVariables.flatMap(_ => List(0.0, 1.0))

    def collectMaps[T, U](m: Seq[Map[T, U]]): Map[T, Seq[U]] = {
      m.flatten.groupBy(_._1).mapValues(seqTuples => seqTuples.map(_._2))
    }

    val finalValues = 1.to(niterations).foldLeft(initialValues) {
      case (values, _) =>
        val grads: Seq[Map[Real, Double]] = (1 to nsamples) map { _ =>
          val samples = sampleFromGuide()
          val inputs = variables.zip(samples ++ values).toMap
          val outputs = cf(inputs)
          outputs
        }
        val perDimGradSamples = collectMaps(grads)
        val perDimGrads = perDimGradSamples.mapValues(samples =>
          samples.sum * 1.0 / nsamples.toDouble)
        (values zip variables.map(perDimGrads)).map {
          case (v, g) => v + stepsize * g
        }
    }

  }
}
