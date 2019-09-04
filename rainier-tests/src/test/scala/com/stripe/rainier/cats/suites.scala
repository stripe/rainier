package com.stripe.rainier
package cats

import com.stripe.rainier.compute.{Constant, Infinity, NegInfinity, Real}
import com.stripe.rainier.core._
import com.stripe.rainier.scalacheck._
import com.stripe.rainier.sampler.RNG
import com.stripe.rainier.compute.Evaluator

import _root_.cats.Eq
import _root_.cats.kernel.instances.MapInstances
import _root_.cats.tests.CatsSuite
import _root_.cats.laws.discipline._

class CategoricalSuite extends CatsSuite with MapInstances {

  implicit val rng: RNG = RNG.default
  implicit val evaluator: Evaluator = new Evaluator(Map.empty)

  def eqBigDecimal(epsilon: Double): Eq[BigDecimal] =
    Eq.instance { (left, right) =>
      ((left - right) / left) < epsilon && ((left - right) / right) < epsilon
    }

  implicit val eqReal: Eq[Real] = {
    val bde = eqBigDecimal(1e-6)
    Eq.instance { (left, right) =>
      (left, right) match {
        case (Infinity, Infinity) | (NegInfinity, NegInfinity) => true
        case (Constant(a), Constant(b))                        => bde.eqv(a, b)
        case _                                                 => false
      }
    }
  }

  implicit def eqCategorical[A: Eq]: Eq[Categorical[A]] = {
    implicit val mapEq: Eq[Map[A, Real]] = catsKernelStdEqForMap[A, Real]
    Eq.instance((x, y) => Eq.eqv(x.pmf, y.pmf)(mapEq))
  }

  checkAll("Categorical[Int]", MonadTests[Categorical].monad[Int, Int, Int])
}

class GeneratorSuite extends CatsSuite {

  implicit val rng: RNG = RNG.default
  implicit val evaluator: Evaluator = new Evaluator(Map.empty)

  implicit def eqGenerator[A](implicit EqA: Eq[A]): Eq[Generator[A]] =
    Eq.instance((x, y) => EqA.eqv(x.get, y.get))

  checkAll("Generator[Int]", MonadTests[Generator].monad[Int, Int, Int])
}

class RandomVariableSuite extends CatsSuite {

  implicit def eqRandomVariable[A](implicit EqA: Eq[A]): Eq[RandomVariable[A]] =
    Eq.instance((x, y) => EqA.eqv(x.value, y.value))

  checkAll("RandomVariable[Int]",
           MonadTests[RandomVariable].monad[Int, Int, Int])
}
