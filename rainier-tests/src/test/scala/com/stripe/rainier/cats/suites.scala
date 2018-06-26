package com.stripe.rainier.cats

import com.stripe.rainier.core._
import com.stripe.rainier.scalacheck._
import com.stripe.rainier.sampler.RNG
import com.stripe.rainier.compute.Evaluator

import cats.Eq
import cats.tests.CatsSuite
import cats.laws.discipline._

class GeneratorSuite extends CatsSuite {

  implicit val rng: RNG = RNG.default
  implicit val evaluator: Evaluator = new Evaluator(Map.empty)

  implicit def eqGenerator[A](implicit EqA: Eq[A]): Eq[Generator[A]] =
    Eq.instance((x, y) => EqA.eqv(x.get, y.get))

  checkAll("Generator[Int]",
           MonadTests[Generator].stackUnsafeMonad[Int, Int, Int])
}

class RandomVariableSuite extends CatsSuite {

  implicit def eqRandomVariable[A](implicit EqA: Eq[A]): Eq[RandomVariable[A]] =
    Eq.instance((x, y) => EqA.eqv(x.value, y.value))

  checkAll("RandomVariable[Int]",
           MonadTests[RandomVariable].monad[Int, Int, Int])
}
