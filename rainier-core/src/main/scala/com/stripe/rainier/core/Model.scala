package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

case class Model(private[rainier] val likelihoods: List[Real],
                 track: Set[Real]) {
  def prior: Model = Model.track(track ++ likelihoods)
  def merge(other: Model) =
    Model(likelihoods ++ other.likelihoods, track ++ other.track)

  def sample(sampler: Sampler, nChains: Int = 4)(implicit rng: RNG,
                                                 progress: Progress =
                                                   SilentProgress): Trace = {
    val chains = 1
      .to(nChains)
      .toList
      .map { i =>
        sampler.sample(i, density(), progress)
      }
      .toList
    Trace(chains, this)
  }

  def optimize[T, U](t: T)(implicit toGen: ToGenerator[T, U], rng: RNG): U = {
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

  def sample[T, U](t: T, sampler: Sampler = Sampler.default)(
      implicit toGen: ToGenerator[T, U],
      rng: RNG,
      progress: Progress = SilentProgress): List[U] = {
    val gen = toGen(t)
    val model = Model.track(gen.requirements)
    val trace = model.sample(sampler, 1)
    trace.thin(10).predict(gen)
  }

  def apply(reals: Real*): Model = track(reals.toSet)
  def track(track: Set[Real]): Model = Model(List(Real.zero), track)
  def likelihood(real: Real) = Model(List(real), Set.empty[Real])
  def likelihoods(reals: List[Real]) = Model(reals, Set.empty[Real])

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
