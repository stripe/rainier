package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler._

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
class RandomVariable[+T](val value: T, val targets: Set[Target]) {

  def flatMap[U](fn: T => RandomVariable[U]): RandomVariable[U] = {
    val rv = fn(value)
    new RandomVariable(rv.value, targets ++ rv.targets)
  }

  def map[U](fn: T => U): RandomVariable[U] =
    new RandomVariable(fn(value), targets)

  def zip[U](other: RandomVariable[U]): RandomVariable[(T, U)] =
    new RandomVariable((value, other.value), targets ++ other.targets)

  lazy val targetGroup = TargetGroup(targets, 500)
  lazy val dataFn =
    Compiler.default.compileTargets(targetGroup, true, 4)

  def density() =
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

  def densityAtOrigin: Double = {
    val inputs = new Array[Double](dataFn.numInputs)
    val globals = new Array[Double](dataFn.numGlobals)
    val outputs = new Array[Double](dataFn.numOutputs)
    dataFn(inputs, globals, outputs)
    outputs(0)
  }

  //this is really just here to allow destructuring in for{}
  def withFilter(fn: T => Boolean): RandomVariable[T] =
    if (fn(value))
      this
    else
      RandomVariable(value, Real.zero.log)

  def toGenerator[U](
      implicit tg: ToGenerator[T, U]): RandomVariable[Generator[U]] =
    new RandomVariable(tg(value), targets)

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
}

/**
  * The main probability monad used in Rainier for constructing probabilistic programs which can be sampled
  */
object RandomVariable {
  def apply[A](a: A, density: Real): RandomVariable[A] =
    new RandomVariable(a, Set(Target(density)))

  def apply[A](a: A): RandomVariable[A] =
    apply(a, Real.zero)

  def fromDensity(density: Real): RandomVariable[Unit] =
    apply((), density)

  def traverse[A](rvs: Seq[RandomVariable[A]]): RandomVariable[Seq[A]] = {

    def go(accum: RandomVariable[Seq[A]], rv: RandomVariable[A]) = {
      for {
        v <- rv
        vs <- accum
      } yield v +: vs
    }

    rvs
      .foldLeft[RandomVariable[Seq[A]]](apply(Seq[A]())) {
        case (accum, elem) => go(accum, elem)
      }
      .map(_.reverse)
  }

  def traverse[K, V](
      rvs: Map[K, RandomVariable[V]]): RandomVariable[Map[K, V]] = {
    def go(accum: RandomVariable[Map[K, V]], k: K, rv: RandomVariable[V]) = {
      for {
        v <- rv
        vs <- accum
      } yield vs + (k -> v)
    }

    rvs
      .foldLeft[RandomVariable[Map[K, V]]](apply(Map[K, V]())) {
        case (accum, (k, v)) => go(accum, k, v)
      }
  }

  def fill[A](k: Int)(fn: => RandomVariable[A]): RandomVariable[Seq[A]] =
    traverse(List.fill(k)(fn))
}
