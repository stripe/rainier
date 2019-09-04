package com.stripe.rainier.cats

import com.stripe.rainier.compute.Real
import com.stripe.rainier.core.{Categorical, Generator, RandomVariable}
import com.stripe.rainier.sampler.RNG
import _root_.cats.Monad

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object `package` {
  implicit val rainierMonadCategorical: Monad[Categorical] =
    new MonadCategorical
  implicit val rainierMonadGenerator: Monad[Generator] = new MonadGenerator
  implicit val rainierMonadRandomVariable: Monad[RandomVariable] =
    new MonadRandomVariable
}

private[cats] class MonadCategorical extends Monad[Categorical] {
  def pure[A](x: A): Categorical[A] = Categorical(Map(x -> Real.one))

  override def map[A, B](fa: Categorical[A])(f: A => B): Categorical[B] =
    fa.map(f)

  override def product[A, B](
      fa: Categorical[A],
      fb: Categorical[B]
  ): Categorical[(A, B)] = fa.zip(fb)

  override def flatMap[A, B](fa: Categorical[A])(
      f: A => Categorical[B]): Categorical[B] =
    fa.flatMap(f)

  def tailRecM[A, B](a: A)(
      f: A => Categorical[Either[A, B]]): Categorical[B] = {
    @tailrec
    def run(acc: Map[B, Real],
            queue: Queue[(Either[A, B], Real)]): Map[B, Real] =
      if (queue.isEmpty) acc
      else {
        queue.head match {
          case (Left(a), r) =>
            run(acc, queue.tail ++ f(a).pmf.mapValues(_ * r))
          case (Right(b), r) =>
            run(acc.updated(b, acc.getOrElse(b, Real.zero) + r), queue.tail)
        }
      }
    val pmf = run(Map.empty, f(a).pmf.to[Queue])
    Categorical[B](pmf)
  }
}

private[cats] class MonadGenerator extends Monad[Generator] {
  def pure[A](x: A): Generator[A] = Generator.constant(x)

  override def map[A, B](fa: Generator[A])(f: A => B): Generator[B] =
    fa.map(f)

  override def product[A, B](fa: Generator[A],
                             fb: Generator[B]): Generator[(A, B)] =
    fa.zip(fb)

  def flatMap[A, B](fa: Generator[A])(f: A => Generator[B]): Generator[B] =
    fa.flatMap(f)

  def tailRecM[A, B](a: A)(f: A => Generator[Either[A, B]]): Generator[B] = {
    @tailrec
    def run(g: Generator[Either[A, B]], r: RNG, n: Numeric[Real]): B =
      g match {
        case Generator.Const(_, Left(a))  => run(f(a), r, n)
        case Generator.Const(_, Right(b)) => b
        case Generator.From(_, fromFn) =>
          fromFn(r, n) match {
            case Left(a)  => run(f(a), r, n)
            case Right(b) => b
          }
      }
    val step = f(a)
    Generator.require(step.requirements)(run(step, _, _))
  }
}

private[cats] class MonadRandomVariable extends Monad[RandomVariable] {
  def pure[A](x: A): RandomVariable[A] = RandomVariable(x)

  override def map[A, B](fa: RandomVariable[A])(f: A => B): RandomVariable[B] =
    fa.map(f)

  def flatMap[A, B](fa: RandomVariable[A])(
      f: A => RandomVariable[B]): RandomVariable[B] =
    fa.flatMap(f)

  override def product[A, B](fa: RandomVariable[A],
                             fb: RandomVariable[B]): RandomVariable[(A, B)] =
    fa.zip(fb)

  @tailrec final def tailRecM[A, B](a: A)(
      f: A => RandomVariable[Either[A, B]]): RandomVariable[B] =
    f(a).value match {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => RandomVariable(b)
    }
}
