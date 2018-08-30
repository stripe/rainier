package com.stripe.rainier.sampler

import com.stripe.rainier.compute._

trait Guide {
  def density: Real // density of variational distributions
  def params: Seq[Variable] // params to optimize
  def forward: Map[Variable, NonConstant] // map between original model params and f(params, eps)
  def generate(implicit rng: RNG): Seq[Double]
  def sample(paramSettings: Map[Variable, Double]): Map[Variable, Double]
  def init: Map[Variable, Double]

  private def transformDensity(targetDensity: Real): Real = {
    RealOps.substituteVariable(targetDensity, forward)
  }
  def loss(targetDensity: Real): Real = {
    transformDensity(targetDensity) - density
  }
}

case class MeanFieldNormalGuide(targetVariables: Seq[Variable])(
    implicit rng: RNG)
    extends Guide {
  private val densities: Map[(Variable, Variable), Real] =
    targetVariables
      .map(tv => {
        val mu = new Variable
        val sigma = new Variable
        val eps = new Variable
        val d = (mu + sigma * eps).pow(2)
        (mu, eps) -> d
      })
      .toMap

  override def params: Seq[Variable] = densities.keys.toSeq.flatMap {
    case (mu, sigma) => List(mu, sigma)
  }
  override def density: Real = {
    densities.foldLeft(Real(0.0)) {
      case (joint, (_, dens)) =>
        joint + dens
    }
  }
  override def forward: Map[Variable, NonConstant] = {
    targetVariables.zip(densities.map(_._2)).toMap
  }
  override def generate(implicit rng: RNG) = {
    densities.map(_ => rng.standardNormal).toSeq
  }
  override def sample(
      paramSettings: Map[Variable, Double]): Map[Variable, Double] = {
    targetVariables
      .zip(generate.zip(densities).map {
        case (eps, ((mu, sigma), _)) => {
          paramSettings(mu) + paramSettings(sigma) * eps
        }
      })
      .toMap
  }
  override def init: Map[Variable, Double] = {
    densities.flatMap {
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

  override def sample(
      context: Context,
      warmUpIterations: Int,
      iterations: Int,
      keepEvery: Int)(implicit rng: RNG): List[Array[Double]] = {

    val modelVariables = context.variables
    val guide = MeanFieldNormalGuide(modelVariables)

    val loss = guide.loss(context.density)
    val guideVariables = RealOps.variables(guide.density)
    val guideParams = guide.params
    val guideEpsilons = (guideVariables.toSet -- guideParams.toSet).toSeq

    val gradients = Gradient.derive(guideParams, loss)
    val cf = context.compiler.compile(guideVariables, gradients)

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

    List.fill(iterations)({
      val samples = guide.sample(finalMap)
      modelVariables.map(samples).toArray
    })
  }

}
