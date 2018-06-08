package com.stripe.rainier.cats

import com.stripe.rainier.core.Generator
import com.stripe.rainier.core.RandomVariable
import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.RNG

import _root_.cats.Monad

import scala.annotation.tailrec

object `package` {
  implicit val rainierMonadGenerator: Monad[Generator] = new MonadGenerator
  implicit val rainierMonadRandomVariable: Monad[RandomVariable] =
    new MonadRandomVariable
}

private[cats] class MonadGenerator extends Monad[Generator] {
  def pure[A](x: A): Generator[A] = Generator(x)

  override def map[A, B](fa: Generator[A])(f: A => B): Generator[B] =
    fa.map(f)

  def flatMap[A, B](fa: Generator[A])(f: A => Generator[B]): Generator[B] =
    fa.flatMap(f)

  // note: currently not stack safe
  def tailRecM[A, B](a: A)(f: A => Generator[Either[A, B]]): Generator[B] =
    new Generator[B] {
      lazy val step = f(a)
      def requirements: Set[Real] = step.requirements
      def get(implicit r: RNG, n: Numeric[Real]): B =
        step.get match {
          case Right(b) => b
          case Left(aa) => tailRecM(aa)(f).get
        }
    }
}

private[cats] class MonadRandomVariable extends Monad[RandomVariable] {
  def pure[A](x: A): RandomVariable[A] = RandomVariable(x)

  override def map[A, B](fa: RandomVariable[A])(f: A => B): RandomVariable[B] =
    fa.map(f)

  def flatMap[A, B](fa: RandomVariable[A])(
      f: A => RandomVariable[B]): RandomVariable[B] =
    fa.flatMap(f)

  @tailrec final def tailRecM[A, B](a: A)(
      f: A => RandomVariable[Either[A, B]]): RandomVariable[B] =
    f(a).value match {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => RandomVariable(b)
    }
}
