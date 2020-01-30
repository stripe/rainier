package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._
import com.stripe.rainier.optimizer._

case class Model(private[rainier] val likelihoods: List[Real],
                 track: Set[Real]) {
  def prior: Model = Model.track(track ++ likelihoods)
  def merge(other: Model) =
    Model(likelihoods ++ other.likelihoods, track ++ other.track)

  def sample(sampler: Sampler, nChains: Int = 4)(implicit rng: RNG): Trace = {
    val range = 1.to(nChains)
    val chains = range.map { _ =>
      sampler.sample(density())
    }.toList
    Trace(chains, this)
  }

  def optimize[T, U](t: T)(implicit toGen: ToGenerator[T, U]): U = {
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

  override def toString = s"Model[${parameters.size}]"
}

object Model {
  val empty = Model.likelihood(Real.zero)

  def sample[T, U](t: T, sampler: Sampler = Sampler.default)(
      implicit toGen: ToGenerator[T, U],
      rng: RNG): List[U] = {
    val gen = toGen(t)
    val model = Model.track(gen.requirements)
    val trace = model.sample(sampler, 1)
    trace.thin(10).predict(gen)
  }

  def apply(reals: Real*): Model = track(reals.toSet)
  def track(track: Set[Real]): Model = Model(List(Real.zero), track)
  def likelihood(real: Real) = Model(List(real), Set.empty[Real])
  def likelihoods(reals: List[Real]) = Model(reals, Set.empty[Real])

  def observe[Y](y: Y, dist: Distribution[Y]): Model =
    Model.likelihood(dist.likelihoodFn(y))

  val NumSplits = 8
  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model = {
    val f = dist.likelihoodFn
    if (ys.size > NumSplits) {
      val (init, splits) = split(ys, NumSplits)
      val initReal = f.encode(init)
      Model.likelihoods(List(initReal, Real.sum(splits.map { s =>
        f.encode(s)
      })))
    } else
      Model.likelihood(f.encode(ys))
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    if (ys.size > NumSplits) {
      val (initX, splitsX) = split(xs, NumSplits)
      val (initY, splitsY) = split(ys, NumSplits)

      Model.likelihoods(
        List(fn.encode(initX).likelihoodFn.encode(initY),
             Real.sum(splitsX.zip(splitsY).map {
               case (sx, sy) =>
                 fn.encode(sx).likelihoodFn.encode(sy)
             })))
    } else
      Model.likelihood(fn.encode(xs).likelihoodFn.encode(ys))
  }

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val likelihoods = (xs.zip(ys)).map {
      case (x, y) => fn(x).likelihoodFn(y)
    }

    Model.likelihood(Real.sum(likelihoods))
  }

  private def split[T](ts: Seq[T], n: Int): (List[T], List[List[T]]) = {
    val splitSize = (ts.size - 1) / n
    val initSize = ts.size - (splitSize * n)
    val init = ts.take(initSize).toList
    val splits = ts.drop(initSize).grouped(splitSize).toList.map(_.toList)
    (init, splits)
  }
}
