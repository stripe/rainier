package rainier.sampler

import rainier.compute._
import rainier.core._

trait Guide {
  def density: Real // density of variational distributions
  def params: Seq[Variable] // params to optimize
  def forward: Map[Variable, Real] // map between original model params and f(params, eps)
  def generate(implicit rng: RNG): Seq[Double]
  def sample(paramSettings: Map[Variable, Double]): Map[Variable, Double]
  def init: Map[Variable, Double]

  private def transformDensity(targetDensity: Real): Real = {
    Real.substituteVariable(targetDensity, forward)
  }
  def loss(targetDensity: Real): Real = {
    transformDensity(targetDensity) - density
  }
}

case class MeanFieldNormalGuide(targetVariables: Seq[Variable])(
    implicit rng: RNG)
    extends Guide {
  private val normals: Map[(Variable, Variable), (Real, Continuous)] =
    targetVariables
      .map(tv => {
        val mu = new Variable
        val sigma = new Variable
        val dist = Normal(mu, sigma)
        ((mu, sigma), (dist.param.density.variables.head, dist))
      })
      .toMap

  override def params: Seq[Variable] = normals.keys.toSeq.flatMap {
    case (mu, sigma) => List(mu, sigma)
  }
  override def density: Real = {
    normals.foldLeft(Real(0.0)) {
      case (joint, ((mu, sigma), (eps, dist))) =>
        joint + dist.realLogDensity(mu + sigma * eps)
    }
  }
  override def forward: Map[Variable, Real] = {
    (targetVariables zip (normals.map {
      case ((mu, sigma), (eps, _)) => {
        mu + sigma * eps
      }
    })).toMap
  }
  override def generate(implicit rng: RNG) = {
    normals.map(_ => rng.standardNormal).toSeq
  }
  override def sample(
      paramSettings: Map[Variable, Double]): Map[Variable, Double] = {
    (targetVariables zip (generate zip normals).map {
      case (eps, ((mu, sigma), _)) => {
        paramSettings(mu) + paramSettings(sigma) * eps
      }
    }).toMap
  }
  override def init: Map[Variable, Double] = {
    normals.flatMap {
      case ((mu, sigma), _) => List((mu -> 0.0), (sigma -> 1.0))
    }
  }
}

case class Variational(tolerance: Double,
                       maxIterations: Int,
                       nIterations: Int,
                       nSamples: Int,
                       stepsize: Double)
    extends Sampler {

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

    val modelVariables = density.variables
    val guide = MeanFieldNormalGuide(modelVariables)

    val loss = guide.loss(density)
    val guideVariables = guide.density.variables
    val guideParams = guide.params
    val guideEpsilons = (guideVariables.toSet -- guideParams.toSet).toSeq

    val gradients = guideParams.map((loss.variables zip loss.gradient).toMap)
    val cf = Compiler.default.compile(guideVariables, gradients)

    val initialValues = guideParams.map(guide.init)

    val finalValues = 1.to(nIterations).foldLeft(initialValues) {
      case (values, _) =>
        val gradSamples: Seq[Array[Double]] = (1 to nSamples) map { _ =>
          val epsilons = guideEpsilons zip guide.generate
          val settings = guideParams zip values
          val inputs = guideVariables.map((epsilons ++ settings).toMap)
          val outputs = cf(inputs.toArray)
          outputs
        }
        val perDimGradSamples = gradSamples.transpose
        val perDimGrads = perDimGradSamples.map(samples =>
          samples.sum * 1.0 / nSamples.toDouble)
        (values zip perDimGrads).map {
          case (v, g) => v + stepsize * g
        }
    }
    val finalMap = (guideParams zip finalValues).toMap

    Stream.continually {
      val samples = guide.sample(finalMap)
      Sample(true, new Evaluator(samples))
    }
  }

}
