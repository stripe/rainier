package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

class Model(private[core] val base: Real,
            private[core] val batches: List[Batch[Real]]) {
  def +(other: Model) = new Model(base + other.base, batches ++ other.batches)

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

  lazy val compiledModel = Compiler.default.compile(base, batches)
  def parameters: List[Parameter] = compiledModel.parameters

  private[rainier] def density(): DensityFunction = compiledModel.density()
}

object Model {
  def apply(real: Real): Model = new Model(real, Nil)
  def apply(batch: Batch[Real]): Model = new Model(Real.zero, List(batch))

  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model =
    Model(dist.likelihoodFn.encode(ys))

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val likelihoods = (xs.zip(ys)).map {
      case (x, y) => fn(x).likelihoodFn(y)
    }

    Model(Real.sum(likelihoods))
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    val batch = fn.encode(xs).flatMap { dist =>
      dist.likelihoodFn.encode(ys)
    }
    Model(batch)
  }
}
