package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

class Model(private[rainier] val likelihoods: List[Real],
            val track: Set[Real]) {
  def prior: Model = Model.track(track ++ likelihoods)
  def merge(other: Model) =
    new Model(likelihoods ++ other.likelihoods, track ++ other.track)

  def sample(config: SamplerConfig = SamplerConfig.default, nChains: Int = 4)(
      implicit rng: RNG = RNG.default,
      progress: Progress = SilentProgress): Trace = {
    val results = 1
      .to(nChains)
      .toList
      .map { i =>
        Driver.sample(i, config, density(), progress)
      }
      .toList
    Trace(results.map(_._1), results.map(_._2), results.map(_._3), this)
  }

  def optimize[T, U](t: T)(implicit toGen: ToGenerator[T, U],
                           rng: RNG = RNG.default): U = {
    val fn = toGen(t).prepare(parameters)
    fn(Optimizer.lbfgs(density()))
  }

  lazy val targetGroup = TargetGroup(likelihoods, track)
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
  val empty = Model.likelihood(Real.zero)

  def sample[T, U](t: T, config: SamplerConfig = SamplerConfig.default)(
      implicit toGen: ToGenerator[T, U],
      rng: RNG = RNG.default,
      progress: Progress = SilentProgress): List[U] = {
    val gen = toGen(t)
    val model = Model.track(gen.requirements)
    model.sample(config).predict(gen)
  }

  def apply[T, U](ts: T*)(implicit toGen: ToGenerator[T, U]): Model =
    track(ts.map(toGen.apply(_).requirements).toSet.flatten)
  def track(track: Set[Real]): Model = new Model(List(Real.zero), track)
  def likelihood(real: Real) = new Model(List(real), Set.empty[Real])
  def likelihoods(reals: List[Real]) = new Model(reals, Set.empty[Real])

  def observe[Y](y: Y, lh: Distribution[Y]): Model =
    observe(List(y), lh)

  def observe[Y](ys: Seq[Y], lh: Distribution[Y]): Model = {
    val (init, splits) = split(ys)
    val initReal = lh.logDensity(init)
    if (splits.isEmpty)
      Model.likelihood(initReal)
    else
      Model.likelihoods(List(initReal, Real.sum(splits.map(lh.logDensity))))
  }

  def observe[X, Y, D <: Distribution[Y]](ys: Seq[Y], lhs: Vec[D]): Model = {
    val (initX, splitsX) = split(lhs)
    if (splitsX.isEmpty) {
      Model.likelihood(initX.columnize.logDensity(ys))
    } else {
      val (initY, splitsY) = split(ys)
      Model.likelihoods(
        List(initX.columnize.logDensity(initY),
             Real.sum(splitsX.zip(splitsY).map {
               case (sx, sy) =>
                 sx.columnize.logDensity(sy)
             })))
    }
  }

  val NumSplits = 8
  def split[T](ts: Vec[T]): (Vec[T], List[Vec[T]]) = {
    val splitSize = (ts.size - 1) / NumSplits
    val initSize = ts.size - (splitSize * NumSplits)
    val init = ts.take(initSize)
    if (splitSize == 0)
      (init, Nil)
    else {
      val splits = 0
        .until(NumSplits)
        .toList
        .map { i =>
          ts.slice(initSize + (i * splitSize), initSize + ((i + 1) * splitSize))
        }
      (init, splits)
    }
  }

  def split[T](ts: Seq[T]): (List[T], List[List[T]]) = {
    val splitSize = (ts.size - 1) / NumSplits
    val initSize = ts.size - (splitSize * NumSplits)
    val init = ts.take(initSize).toList
    if (splitSize == 0)
      (init, Nil)
    else {
      val splits = 0
        .until(NumSplits)
        .toList
        .map { i =>
          ts.slice(initSize + (i * splitSize), initSize + ((i + 1) * splitSize))
            .toList
        }
      (init, splits)
    }
  }
}
