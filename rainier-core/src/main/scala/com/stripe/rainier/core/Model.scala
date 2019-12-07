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

  def writeGraph(path: String, gradient: Boolean = false): Unit = {
    val gradVars = if (gradient) targetGroup.variables else Nil
    val tuples = ("base", targetGroup.base, Map.empty[Variable, Array[Double]]) ::
      targetGroup.batched.zipWithIndex.map {
      case (b, i) =>
        (s"target$i", b.real, b.placeholders)
    }
    RealViz(tuples, gradVars).write(path)
  }

  def writeIRGraph(path: String,
                   gradient: Boolean = false,
                   methodSizeLimit: Option[Int] = None): Unit = {
    val tuples =
      (("base", targetGroup.base) ::
        targetGroup.batched.zipWithIndex.map {
        case (b, i) => (s"target$i" -> b.real)
      })

    RealViz
      .ir(tuples, targetGroup.variables, gradient, methodSizeLimit)
      .write(path)
  }

  def optimize(): Estimate =
    Estimate(Optimizer.lbfgs(density()), this)

  lazy val targetGroup = TargetGroup(targets, 500)
  lazy val dataFn =
    Compiler.default.compileTargets(targetGroup, true, 4)

  private[rainier] def variables: List[Variable] = targetGroup.variables
  private[rainier] def density(): DensityFunction =
    new DensityFunction {
      val nVars = targetGroup.variables.size
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
  def observe[Y](ys: Seq[Y], dist: Distribution[Y]): Model = {
    val target = dist.target(ys)
    Model(Set(target))
  }

  def observe[X, Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): Model = {
    val targets = (xs.zip(ys)).map {
      case (x, y) => fn(x).target(y)
    }

    Model(targets.toSet)
  }

  def observe[X, Y](xs: Seq[X],
                    ys: Seq[Y],
                    fn: Fn[X, Distribution[Y]]): Model = {
    val enc = fn.encoder
    val (v, ph) = enc.encode(xs)
    val dist = fn.xy(v)
    val target = dist.target(ys)
    Model(
      Set(new Target(target.real, target.placeholders ++ ph)))
  }
}
