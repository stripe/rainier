package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

case class Model(private[rainier] val targets: List[Real]) {
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
    Compiler.default.compileTargets(targetGroup)

  def parameters: List[Parameter] = targetGroup.parameters

  private[rainier] def density(): DensityFunction =
    new DensityFunction {
      val nVars = parameters.size
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

object Model {
  val empty = Model(Real.zero)

  def apply(real: Real): Model = Model(List(real))

  def observe[Y](y: Y, dist: Distribution[Y]): Model =
    Model(dist.likelihoodFn(y))

  val NumSplits = 8
  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model = {
    val f = dist.likelihoodFn
    if (ys.size > NumSplits) {
      val (init, splits) = split(ys, NumSplits)
      val initReal = f.encode(init)
      Model(List(initReal, Real.sum(splits.map { s =>
        f.encode(s)
      })))
    } else
      Model(f.encode(ys))
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    if (ys.size > NumSplits) {
      val (initX, splitsX) = split(xs, NumSplits)
      val (initY, splitsY) = split(ys, NumSplits)

      Model(
        List(fn.encode(initX).likelihoodFn.encode(initY),
             Real.sum(splitsX.zip(splitsY).map {
               case (sx, sy) =>
                 fn.encode(sx).likelihoodFn.encode(sy)
             })))
    } else
      Model(List(fn.encode(xs).likelihoodFn.encode(ys)))
  }

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val likelihoods = (xs.zip(ys)).map {
      case (x, y) => fn(x).likelihoodFn(y)
    }

    Model(Real.sum(likelihoods))
  }

  private def split[T](ts: Seq[T], n: Int): (List[T], List[List[T]]) = {
    val splitSize = (ts.size - 1) / n
    val initSize = ts.size - (splitSize * n)
    val init = ts.take(initSize).toList
    val splits = ts.drop(initSize).grouped(splitSize).toList.map(_.toList)
    (init, splits)
  }
}
