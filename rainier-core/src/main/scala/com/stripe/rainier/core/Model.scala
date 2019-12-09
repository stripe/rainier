package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

case class Model(private[rainier] val targets: Set[Target]) {
  def merge(other: Model) = Model(targets ++ other.targets)

  def sample(sampler: Sampler,
             warmupIterations: Int,
             iterations: Int,
             keepEvery: Int = 1,
             nChains: Int = 1)(implicit rng: RNG): Sample = {
    val chains = 1.to(nChains).toList.map { _ =>
      sampler.sample(density(), warmupIterations, iterations, keepEvery)
    }
    Sample(chains, this)
  }

  def optimize(): Estimate =
    Estimate(Optimizer.lbfgs(density()), this)

  lazy val targetGroup = TargetGroup(targets)
  lazy val dataFn =
    Compiler.default.compileTargets(targetGroup, true)

  private[rainier] def density(): DensityFunction =
    Model.density(dataFn)
}

object Model {
  def apply(real: Real): Model = Model(Set(new Target(real)))

  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model =
    Model(dist.likelihoodFn.encode(ys))

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val likelihoods = (xs.zip(ys)).map {
      case (x, y) => fn(x).likelihoodFn(y)
    }

    Model(likelihoods.map(new Target(_)).toSet)
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    val dist = fn.encode(xs)
    Model(dist.likelihoodFn.encode(ys))
  }

  def density(dataFn: DataFunction): DensityFunction =
    new DensityFunction {
      val nVars = dataFn.numParamInputs
      val inputs = new Array[Double](dataFn.numInputs)
      val globals = new Array[Double](dataFn.numGlobals)
      val outputs = new Array[Double](dataFn.numOutputs)
      def update(vars: Array[Double]): Unit = {
        System.arraycopy(vars, 0, inputs, 0, nVars)
        dataFn(inputs, globals, outputs)
      }
      def density = outputs(0)
      def gradient(index: Int) = outputs(index + 1)
    }
}
