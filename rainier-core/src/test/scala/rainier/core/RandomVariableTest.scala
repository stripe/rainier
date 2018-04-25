package rainier.core

import rainier.compute._
import rainier.sampler.RNG
import org.scalatest.FunSuite

class RandomVariableTest extends FunSuite {

  def assertEquiv[S, T](x: RandomVariable[S], y: RandomVariable[S])(
      implicit s: Sampleable[S, T]) {
    List(0.0, 1.0, -1.0).foreach { paramValue =>
      val (xValue, xDensity) = sampleOnce(x, paramValue)
      val (yValue, yDensity) = sampleOnce(y, paramValue)

      assert(xValue == yValue)
      assert(xDensity == yDensity)
    }
  }

  def sampleOnce[S, T](x: RandomVariable[S], paramValue: Double)(
      implicit s: Sampleable[S, T]): (T, Double) = {
    val variables = x.density.variables
    implicit val num = new Evaluator(variables.map { v =>
      v -> paramValue
    }.toMap)
    implicit val rng = RNG.default

    val density = num.toDouble(x.density)
    val value = x.get(rng, s, num)
    (value, density)
  }

  def testMonadLaws[A, B, F, G, H, A1, B1, F1, G1, H1](
      a: RandomVariable[A],
      b: RandomVariable[B],
      x: A,
      f: A => F,
      g: A => RandomVariable[G],
      h: G => RandomVariable[H])(
      implicit a1: Sampleable[A, A1],
      b1: Sampleable[B, B1],
      f1: Sampleable[F, F1],
      g1: Sampleable[G, G1],
      h1: Sampleable[H, H1]
  ) {

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
                  (x: Real) => NonNegative.param)
  }
}
