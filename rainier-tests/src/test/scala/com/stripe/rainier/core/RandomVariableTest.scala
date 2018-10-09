package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG
import org.scalatest.FunSuite

class RandomVariableTest extends FunSuite {

  def assertEquiv[S, T](x: RandomVariable[S], y: RandomVariable[S])(
      implicit s: ToGenerator[S, T]): Unit = {
    List(0.0, 1.0, -1.0).foreach { paramValue =>
      val (xValue, xDensity) = sampleOnce(x, paramValue)
      val (yValue, yDensity) = sampleOnce(y, paramValue)

      assert(xValue == yValue)
      assert((xDensity - yDensity).abs < 0.000001)
    }
  }

  def sampleOnce[S, T](x: RandomVariable[S], paramValue: Double)(
      implicit s: ToGenerator[S, T]): (T, Double) = {
    val variables = x.variables
    implicit val num: Evaluator =
      new Evaluator(variables.map { v =>
        v -> paramValue
      }.toMap)
    implicit val rng: RNG = RNG.default
    val value = x.toGenerator.value.get

    val df = x.density
    df.update(Array.fill(variables.size)(paramValue))
    (value, df.density)
  }

  def testMonadLaws[A, B, F, G, H, A1, B1, F1, G1, H1](
      a: RandomVariable[A],
      b: RandomVariable[B],
      x: A,
      f: A => F,
      g: A => RandomVariable[G],
      h: G => RandomVariable[H])(
      implicit a1: Mapping[A1, A],
      b1: Mapping[B1, B],
      f1: Mapping[F1, F],
      g1: Mapping[G1, G],
      h1: Mapping[H1, H]
  ): Unit = {

    assertEquiv(a.map(f), a.flatMap { x =>
      RandomVariable(f(x))
    })

    assertEquiv(a.flatMap(g).flatMap(h), a.flatMap { x =>
      g(x).flatMap(h)
    })

    assertEquiv(RandomVariable(x).flatMap(g), g(x))

    assertEquiv(
      a.zip(b),
      a.flatMap { x =>
        b.map((x, _))
      }
    )
  }

  test("monad laws with all reals") {
    testMonadLaws(Normal(0, 1).param,
                  Uniform(0, 1).param,
                  Real.one,
                  (x: Real) => x.exp,
                  (x: Real) => Uniform(0, x).param,
                  (x: Real) => Exponential(x).param)
  }
}
