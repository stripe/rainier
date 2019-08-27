package com.stripe.rainier.core

import com.stripe.rainier.compute._
import com.stripe.rainier.sampler.RNG
import org.scalatest.FunSuite

class GeneratorTest extends FunSuite {
  def assertSampleEquals[T](gen: Generator[T], expect: T) = {
    implicit val rng: RNG = RNG.default
    assert(expect === RandomVariable(gen).sample(1).head)
  }

  case class TraverseMe[T](v: T)
  object TraverseMe {
    implicit def generatorTraversal[T, U](
        implicit toGen: ToGenerator[T, U]
    ): GeneratorTraversal[TraverseMe[T], TraverseMe[U]] =
      new GeneratorTraversal[TraverseMe[T], TraverseMe[U]] {
        def apply(tm: TraverseMe[T]): Generator[TraverseMe[U]] = {
          val asGen = toGen(tm.v)
          new Generator[TraverseMe[U]] {
            val requirements: Set[Real] = asGen.requirements
            def get(implicit r: RNG, n: Numeric[Real]): TraverseMe[U] =
              new TraverseMe(asGen.get)
          }
        }
      }
  }

  test("traverse a seq") {
    val input = List(
      Generator.constant(0),
      Generator.constant(1)
    )
    assertSampleEquals(Generator.traverse(input), List(0, 1))
  }

  test("traverse a map") {
    val input = Map(
      "zero" -> Generator.constant(0),
      "one" -> Generator.constant(1)
    )
    assertSampleEquals(Generator.traverse(input), Map("zero" -> 0, "one" -> 1))
  }

  test("custom case class") {
    val input = TraverseMe(Generator.constant(0))
    assertSampleEquals(Generator.traverse(input), TraverseMe(0))
  }
}
