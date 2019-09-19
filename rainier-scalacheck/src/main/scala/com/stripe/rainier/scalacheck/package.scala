package com.stripe.rainier.scalacheck

import com.stripe.rainier.core.{Categorical, Generator, RandomVariable}
import com.stripe.rainier.compute.{Real, ToReal}
import com.stripe.rainier.sampler.{RNG, ScalaRNG}
import org.scalacheck.{Arbitrary, Cogen, Gen}

object `package` {
  import Arbitrary.arbitrary

  implicit lazy val genReal: Gen[Real] = arbitrary[Double].map(Real(_))

  def genConst[A](genA: Gen[A]): Gen[Generator[A]] =
    genA.map(Generator.constant(_))

  def genFromFn[A](genA: Gen[A]): Gen[(RNG, Numeric[Real]) => A] =
    Gen.function2(genA)(cogenRNG, cogenNumericReal)

  def genFrom[A](genA: Gen[A]): Gen[Generator[A]] =
    genFromFn(genA).map(Generator.from(_))

  def genGenerator[A](genA: Gen[A]): Gen[Generator[A]] =
    Gen.oneOf(
      genConst[A](genA),
      genFrom(genA),
      for {
        req <- Gen.containerOf[Set, Real](genReal)
        fn <- genFromFn(genA)
      } yield Generator.require(req)(fn)
    )

  def genCategorical[A](genA: Gen[A]): Gen[Categorical[A]] =
    Gen
      .mapOf[A, Real](Gen.zip(genA, genReal))
      .map(Categorical(_))

  def genRandomVariable[A](genA: Gen[A]): Gen[RandomVariable[A]] =
    for {
      a <- genA
      density <- genReal
    } yield RandomVariable(a, density)

  implicit def arbitraryReal: Arbitrary[Real] =
    Arbitrary(genReal)

  implicit def arbitraryGenerator[A: Arbitrary]: Arbitrary[Generator[A]] =
    Arbitrary(genGenerator(arbitrary[A]))

  implicit def arbitraryRandomVariable[A: Arbitrary]
    : Arbitrary[RandomVariable[A]] =
    Arbitrary(genRandomVariable(arbitrary[A]))

  implicit def arbitraryCategorical[A: Arbitrary]: Arbitrary[Categorical[A]] =
    Arbitrary(genCategorical(arbitrary[A]))

  implicit lazy val cogenRNG: Cogen[RNG] =
    Cogen(_ match {
      case ScalaRNG(seed) => seed
      case other          => sys.error(s"$other RNG currently unsupported")
    })

  // note: currently assuming all evaluators are pure and don't have
  // internal state (other than caching)
  implicit def cogenNumericReal[A](
      implicit CA: Cogen[A],
      tr: ToReal[A]
  ): Cogen[Numeric[Real]] = Cogen(_.getClass.hashCode.toLong)

  implicit def cogenGenerator[A](implicit CA: Cogen[A],
                                 r: RNG,
                                 n: Numeric[Real]): Cogen[Generator[A]] =
    CA.contramap(_.get)

  implicit def cogenRandomVariable[A: Cogen]: Cogen[RandomVariable[A]] =
    Cogen[A].contramap(_.value)
}
